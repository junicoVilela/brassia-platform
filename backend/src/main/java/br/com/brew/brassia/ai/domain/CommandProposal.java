package br.com.brew.brassia.ai.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Um comando que o copiloto propôs e que ninguém executou ainda (AIA-003).
 *
 * <p><strong>Proposta não é comando.</strong> Ela existe como registro justamente para separar o momento em
 * que a IA sugere do momento em que uma pessoa decide — e para que o segundo seja auditável depois. Se a
 * sugestão virasse ação no mesmo instante, não haveria onde registrar quem consentiu, e "a IA fez" seria a
 * única explicação possível para uma alteração de custo ou de qualidade.
 *
 * <p><strong>Quem confirma precisa da permissão do comando, verificada no aceite.</strong> Não a de propor,
 * não a de quem pediu a análise, e não a que a pessoa tinha quando a proposta nasceu: a do comando, agora. A
 * verificação mora no caso de uso e não na borda HTTP — assim ela vale para qualquer chamador que apareça
 * depois, inclusive um que não passe por requisição nenhuma.
 *
 * <p><strong>Proposta vence.</strong> Uma proposta foi feita sobre fatos de um instante: o custo estava
 * incompleto, a medição estava fora da faixa, o tanque estava sujo. Aceitá-la três dias depois é agir sobre
 * um retrato antigo — e o retrato antigo é convincente justamente porque parece atual. Vencer é o que impede
 * uma sugestão esquecida de virar comando no mês seguinte.
 *
 * <p>Decisão é definitiva: aceita ou recusada, a proposta não volta a pendente. Reabri-la apagaria a decisão
 * de alguém; o caminho é propor de novo, com os fatos de agora.
 */
public final class CommandProposal {

    /**
     * Validade de uma proposta.
     *
     * <p>Doze horas cobrem um turno e a leitura do turno seguinte, e não cobrem o fim de semana — que é
     * exatamente o intervalo em que os fatos de um lote mudam sem ninguém olhar.
     */
    public static final Duration VALIDITY = Duration.ofHours(12);

    private final UUID id;
    private final UUID breweryId;
    private final ProposedAction action;
    private final Map<String, String> parameters;
    private final String rationale;
    private final UUID proposedBy;
    private final Instant proposedAt;
    private final Instant expiresAt;
    private final ProposalStatus status;
    private final UUID decidedBy;
    private final Instant decidedAt;
    private final String decisionNote;

    private CommandProposal(UUID id, UUID breweryId, ProposedAction action, Map<String, String> parameters,
            String rationale, UUID proposedBy, Instant proposedAt, Instant expiresAt,
            ProposalStatus status, UUID decidedBy, Instant decidedAt, String decisionNote) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.action = Objects.requireNonNull(action, "ação é obrigatória");
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parâmetros"));
        this.rationale = requireText(rationale, "justificativa", 1000);
        this.proposedBy = Objects.requireNonNull(proposedBy,
                "quem pediu a proposta é obrigatório: a IA não propõe sozinha");
        this.proposedAt = Objects.requireNonNull(proposedAt, "instante da proposta");
        this.expiresAt = Objects.requireNonNull(expiresAt, "validade da proposta");
        this.status = Objects.requireNonNull(status, "status");
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionNote = decisionNote;
        action.validate(this.parameters);
    }

    /**
     * Nasce pendente, com prazo.
     *
     * <p>A justificativa é obrigatória e vem do modelo: uma proposta sem o motivo não dá a quem decide o que
     * ele precisa para decidir, e "a IA sugeriu" não é motivo.
     */
    public static CommandProposal propose(UUID breweryId, ProposedAction action,
            Map<String, String> parameters, String rationale, UUID proposedBy, Instant at) {
        return new CommandProposal(UUID.randomUUID(), breweryId, action, parameters, rationale, proposedBy,
                at, at.plus(VALIDITY), ProposalStatus.PENDING, null, null, null);
    }

    public static CommandProposal reconstitute(UUID id, UUID breweryId, ProposedAction action,
            Map<String, String> parameters, String rationale, UUID proposedBy, Instant proposedAt,
            Instant expiresAt, ProposalStatus status, UUID decidedBy, Instant decidedAt,
            String decisionNote) {
        return new CommandProposal(id, breweryId, action, parameters, rationale, proposedBy, proposedAt,
                expiresAt, status, decidedBy, decidedAt, decisionNote);
    }

    /**
     * Aceita a proposta em nome de quem tem alçada para o comando.
     *
     * <p>As três verificações estão aqui, e não na borda: pendência, prazo e permissão. A permissão exigida é
     * a do comando ({@link ProposedAction#requiredPermission()}), e é conferida contra as permissões de
     * <em>agora</em> de quem está confirmando — quem propôs pode ter perdido a alçada, e quem confirma pode
     * nunca tê-la tido.
     *
     * @param permissions permissões atuais de quem confirma
     * @throws ProposalNotPendingException se já houve decisão
     * @throws ExpiredProposalException    se o prazo passou
     * @throws br.com.brew.brassia.shared.security.ForbiddenException se falta a permissão do comando
     */
    public CommandProposal accept(UUID actorId, Set<String> permissions, String note, Instant at) {
        requirePending();
        requireWithinValidity(at);
        requireCommandAuthority(permissions);
        return decided(ProposalStatus.ACCEPTED, actorId, note, at);
    }

    /**
     * Recusa a proposta.
     *
     * <p>Recusar <strong>não</strong> exige a permissão do comando, e a assimetria é deliberada: dizer "não"
     * a uma sugestão não altera nada no sistema, e exigir alçada para descartar deixaria propostas pendentes
     * acumulando até vencer — o que é pior, porque proposta pendente parece decisão adiada e não decisão
     * tomada.
     */
    public CommandProposal reject(UUID actorId, String note, Instant at) {
        requirePending();
        return decided(ProposalStatus.REJECTED, actorId, note, at);
    }

    /** Verdadeiro quando o prazo passou sem decisão — estado derivado, não gravado. */
    public boolean expiredAt(Instant at) {
        return status == ProposalStatus.PENDING && at.isAfter(expiresAt);
    }

    public boolean pending() {
        return status == ProposalStatus.PENDING;
    }

    private void requirePending() {
        if (status != ProposalStatus.PENDING) {
            throw new ProposalNotPendingException(id, status);
        }
    }

    private void requireWithinValidity(Instant at) {
        if (at.isAfter(expiresAt)) {
            throw new ExpiredProposalException(id, expiresAt);
        }
    }

    private void requireCommandAuthority(Set<String> permissions) {
        if (permissions == null || !permissions.contains(action.requiredPermission())) {
            throw new br.com.brew.brassia.shared.security.ForbiddenException(
                    "confirmar esta proposta exige a permissão " + action.requiredPermission());
        }
    }

    private CommandProposal decided(ProposalStatus decision, UUID actorId, String note, Instant at) {
        return new CommandProposal(id, breweryId, action, parameters, rationale, proposedBy, proposedAt,
                expiresAt, decision,
                Objects.requireNonNull(actorId, "quem decide é obrigatório"),
                Objects.requireNonNull(at, "instante da decisão"), trimToNull(note));
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatória");
        }
        var trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.strip();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("observação da decisão excede 500 caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public ProposedAction action() { return action; }
    public Map<String, String> parameters() { return parameters; }
    public String rationale() { return rationale; }
    public UUID proposedBy() { return proposedBy; }
    public Instant proposedAt() { return proposedAt; }
    public Instant expiresAt() { return expiresAt; }
    public ProposalStatus status() { return status; }
    public UUID decidedBy() { return decidedBy; }
    public Instant decidedAt() { return decidedAt; }
    public String decisionNote() { return decisionNote; }
}
