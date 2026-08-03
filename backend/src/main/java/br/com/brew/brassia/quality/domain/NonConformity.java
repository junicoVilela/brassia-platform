package br.com.brew.brassia.quality.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Não conformidade e o seu tratamento (QLT-002): conter, investigar, agir e verificar eficácia.
 *
 * <p><strong>As fases têm ordem</strong> e o agregado a impõe. Não se investiga o que não se
 * conteve, não se age sem causa raiz e não se verifica sem ação — pular etapa é o jeito mais comum
 * de um CAPA virar teatro.
 *
 * <p><strong>Encerrar exige verificação eficaz.</strong> Verificação com resultado negativo não
 * fecha: devolve à fase de ação, exigindo ação nova. Fechar com verificação negativa produziria um
 * registro dizendo que o problema foi resolvido quando ele não foi.
 *
 * <p>Os prazos são <em>informados</em>, não derivados da severidade: o tempo aceitável para conter
 * depende do porte da operação e do tipo de problema. Enquanto não houver parametrização por
 * cervejaria, derivá-los de regra fixa criaria número sem fonte (débito QLT-002-A).
 */
public final class NonConformity {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final String title;
    private final String description;
    private final NonConformitySource source;
    private final UUID deviationId;
    private final Severity severity;
    private NonConformityStatus status;
    private final LocalDate containmentDueOn;
    private final LocalDate investigationDueOn;
    private final LocalDate verificationDueOn;
    private Containment containment;
    private Investigation investigation;
    private final List<CapaAction> actions;
    private final List<Verification> verifications;
    private final Instant openedAt;
    private final UUID openedBy;
    private Instant closedAt;
    private UUID closedBy;
    private final long lockVersion;

    private NonConformity(UUID id, UUID breweryId, String code, String title, String description,
            NonConformitySource source, UUID deviationId, Severity severity, NonConformityStatus status,
            LocalDate containmentDueOn, LocalDate investigationDueOn, LocalDate verificationDueOn,
            Containment containment, Investigation investigation, List<CapaAction> actions,
            List<Verification> verifications, Instant openedAt, UUID openedBy, Instant closedAt,
            UUID closedBy, long lockVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = Texts.require(code, "código", 40);
        this.title = Texts.require(title, "título", 200);
        this.description = Texts.require(description, "descrição", 2000);
        this.source = Objects.requireNonNull(source, "origem é obrigatória");
        this.deviationId = deviationId;
        this.severity = Objects.requireNonNull(severity, "severidade é obrigatória");
        this.status = Objects.requireNonNull(status, "situação");
        this.containmentDueOn = Objects.requireNonNull(containmentDueOn, "prazo de contenção");
        this.investigationDueOn = Objects.requireNonNull(investigationDueOn, "prazo de investigação");
        this.verificationDueOn = Objects.requireNonNull(verificationDueOn, "prazo de verificação");
        this.containment = containment;
        this.investigation = investigation;
        this.actions = new ArrayList<>(Objects.requireNonNull(actions, "ações"));
        this.verifications = new ArrayList<>(Objects.requireNonNull(verifications, "verificações"));
        this.openedAt = Objects.requireNonNull(openedAt, "abertura");
        this.openedBy = Objects.requireNonNull(openedBy, "responsável pela abertura");
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.lockVersion = lockVersion;
        requireDeadlineOrder();
        if (source == NonConformitySource.DEVIATION && deviationId == null) {
            throw new IllegalArgumentException("não conformidade originada de desvio precisa apontar o desvio");
        }
    }

    public static NonConformity open(UUID breweryId, String code, String title, String description,
            NonConformitySource source, UUID deviationId, Severity severity, LocalDate containmentDueOn,
            LocalDate investigationDueOn, LocalDate verificationDueOn, Instant openedAt, UUID openedBy) {
        return new NonConformity(UUID.randomUUID(), breweryId, code, title, description, source, deviationId,
                severity, NonConformityStatus.OPEN, containmentDueOn, investigationDueOn, verificationDueOn,
                null, null, List.of(), List.of(), openedAt, openedBy, null, null, 0);
    }

    public static NonConformity reconstitute(UUID id, UUID breweryId, String code, String title,
            String description, NonConformitySource source, UUID deviationId, Severity severity,
            NonConformityStatus status, LocalDate containmentDueOn, LocalDate investigationDueOn,
            LocalDate verificationDueOn, Containment containment, Investigation investigation,
            List<CapaAction> actions, List<Verification> verifications, Instant openedAt, UUID openedBy,
            Instant closedAt, UUID closedBy, long lockVersion) {
        return new NonConformity(id, breweryId, code, title, description, source, deviationId, severity,
                status, containmentDueOn, investigationDueOn, verificationDueOn, containment, investigation,
                actions, verifications, openedAt, openedBy, closedAt, closedBy, lockVersion);
    }

    public void contain(String description, Instant at, UUID by) {
        requireStatus(NonConformityStatus.OPEN, "contenção");
        this.containment = new Containment(description, at, by);
        this.status = NonConformityStatus.CONTAINED;
    }

    public void investigate(String rootCause, String method, Instant at, UUID by) {
        requireStatus(NonConformityStatus.CONTAINED, "investigação");
        this.investigation = new Investigation(rootCause, method, at, by);
        this.status = NonConformityStatus.INVESTIGATED;
    }

    /**
     * Planeja ação. Aceito depois da investigação e também depois de uma verificação ineficaz —
     * que é justamente quando uma ação nova precisa ser desenhada.
     */
    public CapaAction planAction(CapaActionKind kind, String description, String owner, LocalDate dueOn) {
        if (status != NonConformityStatus.INVESTIGATED && status != NonConformityStatus.ACTION_PLANNED) {
            throw new PhaseOutOfOrderException(code, status, "planejamento de ação");
        }
        var action = CapaAction.plan(kind, description, owner, dueOn);
        actions.add(action);
        this.status = NonConformityStatus.ACTION_PLANNED;
        return action;
    }

    public void completeAction(UUID actionId, Instant at) {
        action(actionId).orElseThrow(() -> new IllegalArgumentException("ação inexistente")).complete(at);
    }

    /**
     * Verifica a eficácia. Ineficaz <strong>devolve à fase de ação</strong>: o tratamento volta a
     * exigir ação nova, e a verificação negativa fica no histórico como evidência de que a
     * primeira tentativa não resolveu.
     */
    public Verification verify(boolean effective, String evidence, Instant at, UUID by) {
        requireStatus(NonConformityStatus.ACTION_PLANNED, "verificação");
        if (actions.stream().noneMatch(CapaAction::completed)) {
            throw new VerificationRequiredException(code,
                    "verificar eficácia antes de concluir qualquer ação não prova nada");
        }
        var verification = new Verification(effective, evidence, at, by);
        verifications.add(verification);
        this.status = effective ? NonConformityStatus.VERIFIED : NonConformityStatus.INVESTIGATED;
        return verification;
    }

    /** Encerra. Só a partir de uma verificação eficaz — é o critério da história. */
    public void close(Instant at, UUID by) {
        if (status == NonConformityStatus.CLOSED) {
            throw new IllegalStateException("não conformidade já encerrada");
        }
        if (status != NonConformityStatus.VERIFIED) {
            throw new VerificationRequiredException(code,
                    "encerrar exige verificação de eficácia com resultado positivo");
        }
        this.status = NonConformityStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(at, "instante do encerramento");
        this.closedBy = Objects.requireNonNull(by, "responsável pelo encerramento");
    }

    /**
     * Fases com prazo vencido na data informada — derivado, nunca coluna. Uma NC encerrada não
     * tem fase atrasada: o que passou, passou dentro do tratamento que se concluiu.
     */
    public List<String> overduePhases(LocalDate today) {
        Objects.requireNonNull(today, "data de referência");
        var overdue = new ArrayList<String>();
        if (status.terminal()) {
            return overdue;
        }
        if (containment == null && containmentDueOn.isBefore(today)) {
            overdue.add("containment");
        }
        if (investigation == null && investigationDueOn.isBefore(today)) {
            overdue.add("investigation");
        }
        if (status != NonConformityStatus.VERIFIED && verificationDueOn.isBefore(today)) {
            overdue.add("verification");
        }
        actions.stream().filter(a -> a.overdue(today)).forEach(a -> overdue.add("action:" + a.id()));
        return overdue;
    }

    public boolean overdue(LocalDate today) {
        return !overduePhases(today).isEmpty();
    }

    public Optional<CapaAction> action(UUID actionId) {
        return actions.stream().filter(a -> a.id().equals(actionId)).findFirst();
    }

    /** Encerrar a NC encerra o desvio que a originou: é o ciclo da QLT-001 se completando. */
    public Optional<UUID> deviationToClose() {
        return status.terminal() ? Optional.ofNullable(deviationId) : Optional.empty();
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public NonConformitySource source() {
        return source;
    }

    public Optional<UUID> deviationId() {
        return Optional.ofNullable(deviationId);
    }

    public Severity severity() {
        return severity;
    }

    public NonConformityStatus status() {
        return status;
    }

    public LocalDate containmentDueOn() {
        return containmentDueOn;
    }

    public LocalDate investigationDueOn() {
        return investigationDueOn;
    }

    public LocalDate verificationDueOn() {
        return verificationDueOn;
    }

    public Optional<Containment> containment() {
        return Optional.ofNullable(containment);
    }

    public Optional<Investigation> investigation() {
        return Optional.ofNullable(investigation);
    }

    public List<CapaAction> actions() {
        return List.copyOf(actions);
    }

    public List<Verification> verifications() {
        return List.copyOf(verifications);
    }

    public Instant openedAt() {
        return openedAt;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public UUID closedBy() {
        return closedBy;
    }

    public long lockVersion() {
        return lockVersion;
    }

    private void requireStatus(NonConformityStatus expected, String attempted) {
        if (status != expected) {
            throw new PhaseOutOfOrderException(code, status, attempted);
        }
    }

    /** Conter depois de investigar não faz sentido nem no papel: os prazos seguem a ordem das fases. */
    private void requireDeadlineOrder() {
        if (investigationDueOn.isBefore(containmentDueOn) || verificationDueOn.isBefore(investigationDueOn)) {
            throw new IllegalArgumentException(
                    "os prazos devem seguir a ordem das fases: contenção, investigação e verificação");
        }
    }
}
