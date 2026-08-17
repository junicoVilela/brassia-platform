package br.com.brew.brassia.container.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um vasilhame retornável, com identidade própria e um ciclo que se repete por anos (CON-001).
 *
 * <p><strong>A identidade é do contêiner, e não da etiqueta.</strong> Um keg reetiquetado continua sendo
 * o mesmo keg, com a mesma inspeção e o mesmo histórico — é por isso que o identificador é objeto
 * separado, e não um campo aqui dentro. A alternativa (o código como chave) faria trocar um adesivo
 * descolado apagar cinco anos de vida do vasilhame.
 *
 * <p><strong>O que voltou do cliente não está pronto.</strong> {@code RETURNED} e {@code EMPTY} são
 * estados diferentes de propósito: o keg que chegou de volta está sujo até que alguém <em>diga</em> que
 * está limpo. Derivar isso da chegada — "voltou, logo está disponível" — encheria com cerveja um
 * vasilhame que ninguém lavou, e o problema apareceria na boca do cliente.
 */
public final class Container {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final ContainerKind kind;
    private final BigDecimal nominalCapacityLiters;
    private Ownership ownership;
    private ContainerCondition condition;
    private ContainerState state;
    private ContainerInspection inspection;
    private Instant retiredAt;
    private String retirementReason;

    private Container(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership, ContainerCondition condition,
            ContainerState state, ContainerInspection inspection, Instant retiredAt,
            String retirementReason) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = requireCode(code);
        this.kind = Objects.requireNonNull(kind, "tipo");
        this.nominalCapacityLiters = requirePositive(nominalCapacityLiters);
        this.ownership = Objects.requireNonNull(ownership, "propriedade");
        this.condition = Objects.requireNonNull(condition);
        this.state = Objects.requireNonNull(state);
        this.inspection = inspection;
        this.retiredAt = retiredAt;
        this.retirementReason = retirementReason;
    }

    /** Nasce vazio e em boa condição — mas <strong>sem inspeção</strong>, que é ato de alguém. */
    public static Container register(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership) {
        return new Container(id, breweryId, code, kind, nominalCapacityLiters, ownership,
                ContainerCondition.GOOD, ContainerState.EMPTY, null, null, null);
    }

    public static Container reconstitute(UUID id, UUID breweryId, String code, ContainerKind kind,
            BigDecimal nominalCapacityLiters, Ownership ownership, ContainerCondition condition,
            ContainerState state, ContainerInspection inspection, Instant retiredAt,
            String retirementReason) {
        return new Container(id, breweryId, code, kind, nominalCapacityLiters, ownership, condition,
                state, inspection, retiredAt, retirementReason);
    }

    // --- inspeção ---

    /** Inspecionar não muda estado nem condição: ele diz que o vasilhame foi olhado, e até quando vale. */
    public void inspect(ContainerInspection inspection) {
        requireAlive();
        this.inspection = Objects.requireNonNull(inspection);
    }

    /**
     * A inspeção em dia é <strong>pré-requisito de encher</strong>, e não um alerta.
     *
     * <p>Um contêiner sem inspeção nenhuma também não passa: "nunca foi inspecionado" é pior que
     * "venceu", e tratar a ausência como aprovação deixaria toda a frota nova fora de qualquer controle.
     */
    public boolean inspectionValidAt(Instant moment) {
        return inspection != null && inspection.validAt(moment);
    }

    // --- ciclo ---

    /**
     * Diz se dá para encher, e explode com o motivo quando não dá.
     *
     * <p>Chamado antes do enchimento (CON-002) e pela tela, que precisa do motivo para mostrar.
     */
    public void requireFillableAt(Instant moment) {
        requireAlive();
        if (condition == ContainerCondition.CONDEMNED) {
            throw ContainerNotFillableException.condemned();
        }
        if (condition == ContainerCondition.DAMAGED) {
            throw ContainerNotFillableException.damaged();
        }
        if (state != ContainerState.EMPTY) {
            throw ContainerNotFillableException.notReady(state);
        }
        if (!inspectionValidAt(moment)) {
            throw ContainerNotFillableException.inspectionExpired();
        }
    }

    public boolean fillableAt(Instant moment) {
        try {
            requireFillableAt(moment);
            return true;
        } catch (ContainerNotFillableException | IllegalStateException recusa) {
            return false;
        }
    }

    public void fill(Instant moment) {
        requireFillableAt(moment);
        this.state = ContainerState.FILLED;
    }

    public void dispatch() {
        transition(ContainerState.FILLED, ContainerState.IN_TRANSIT);
    }

    public void deliver() {
        transition(ContainerState.IN_TRANSIT, ContainerState.AT_CUSTOMER);
    }

    /** Coletado no cliente: volta para casa <strong>sujo</strong>, e não disponível. */
    public void collect() {
        transition(ContainerState.AT_CUSTOMER, ContainerState.RETURNED);
    }

    /**
     * Alguém declara que o vasilhame está limpo e pronto.
     *
     * <p>É <strong>ato explícito</strong>, como a liberação do lote pela qualidade: derivar a limpeza da
     * chegada tornaria "voltou" e "está pronto" a mesma coisa, e elas não são.
     */
    public void releaseToStock() {
        transition(ContainerState.RETURNED, ContainerState.EMPTY);
    }

    public void sendToMaintenance() {
        requireAlive();
        if (state == ContainerState.AT_CUSTOMER || state == ContainerState.IN_TRANSIT) {
            // Não se manda para a oficina o que está na rua: primeiro ele volta.
            throw new IllegalContainerTransitionException(state, ContainerState.IN_MAINTENANCE);
        }
        this.state = ContainerState.IN_MAINTENANCE;
    }

    /** Sai da manutenção consertado e <strong>vazio</strong>, e a condição volta a boa. */
    public void returnFromMaintenance() {
        transition(ContainerState.IN_MAINTENANCE, ContainerState.EMPTY);
        this.condition = ContainerCondition.GOOD;
    }

    // --- condição ---

    public void markDamaged() {
        requireAlive();
        this.condition = ContainerCondition.DAMAGED;
    }

    public void condemn() {
        requireAlive();
        this.condition = ContainerCondition.CONDEMNED;
    }

    // --- fim de vida ---

    /**
     * Baixa: terminal, com motivo, e <strong>não apaga nada</strong>.
     *
     * <p>Não se dá baixa no que está com o cliente ou na rua. O vasilhame que não voltou é perda, que é
     * outro fato e tem outro dono (CON-003) — tratá-los como o mesmo botão faria "sumiu" e "descartei"
     * virarem a mesma linha no inventário.
     */
    public void retire(String reason, Instant at) {
        requireAlive();
        if (state == ContainerState.AT_CUSTOMER || state == ContainerState.IN_TRANSIT) {
            throw new IllegalContainerTransitionException(state, ContainerState.RETIRED);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a baixa precisa de motivo");
        }
        this.state = ContainerState.RETIRED;
        this.retiredAt = Objects.requireNonNull(at);
        this.retirementReason = reason.trim();
    }

    /**
     * Baixa por PERDA: o vasilhame não volta mais, e sai do inventário onde quer que estivesse.
     *
     * <p>É a exceção deliberada à recusa de {@link #retire}, e existe pelo mesmo motivo que a recusa:
     * "sumiu" e "descartei" não podem ser o mesmo botão. Este <strong>é</strong> o botão do "sumiu" —
     * vem de um empréstimo aberto, com motivo e alçada crítica, e o motivo viaja junto para que o
     * inventário nunca precise adivinhar qual dos dois aconteceu.
     */
    public void declareLost(String reason, Instant at) {
        requireAlive();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a perda precisa de motivo");
        }
        this.state = ContainerState.RETIRED;
        this.retiredAt = Objects.requireNonNull(at);
        this.retirementReason = "perdido: " + reason.trim();
    }

    public boolean isRetired() {
        return state == ContainerState.RETIRED;
    }

    public void changeOwnership(Ownership ownership) {
        requireAlive();
        this.ownership = Objects.requireNonNull(ownership);
    }

    private void transition(ContainerState from, ContainerState to) {
        requireAlive();
        if (state != from) {
            throw new IllegalContainerTransitionException(state, to);
        }
        this.state = to;
    }

    private void requireAlive() {
        if (state == ContainerState.RETIRED) {
            throw new ContainerRetiredException();
        }
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

    public ContainerKind kind() {
        return kind;
    }

    public BigDecimal nominalCapacityLiters() {
        return nominalCapacityLiters;
    }

    public Ownership ownership() {
        return ownership;
    }

    public ContainerCondition condition() {
        return condition;
    }

    public ContainerState state() {
        return state;
    }

    public Optional<ContainerInspection> inspection() {
        return Optional.ofNullable(inspection);
    }

    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(retiredAt);
    }

    public Optional<String> retirementReason() {
        return Optional.ofNullable(retirementReason);
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("o contêiner precisa de um código");
        }
        return code.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("a capacidade nominal deve ser positiva");
        }
        return value;
    }
}
