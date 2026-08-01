package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Etapa da agenda de fermentação (FER-004): uma ação com janela planejada, condição de
 * avanço, tolerância e responsável.
 *
 * <p>O planejado nunca é reescrito. Executar registra {@code executedAt} ao lado da janela
 * original, para desvio e justificativa continuarem legíveis no histórico. Replanejar move a
 * janela apenas enquanto a etapa está pendente — o passado não se move.
 */
public final class ScheduleStep {

    private final UUID id;
    private final int sequence;
    private final String name;
    private final ScheduleAction action;
    private final AdvanceCondition condition;
    private final Integer conditionDays;
    private final BigDecimal targetGravity;
    private Instant plannedStart;
    private Instant plannedEnd;
    private final int toleranceHours;
    private final UUID responsibleUserId;
    /** Etapa encadeada segue a anterior no replanejamento; âncora tem data própria. */
    private final boolean dependsOnPrevious;
    private ScheduleStepStatus status;
    private Instant executedAt;
    private String justification;

    private ScheduleStep(UUID id, int sequence, String name, ScheduleAction action, AdvanceCondition condition,
            Integer conditionDays, BigDecimal targetGravity, Instant plannedStart, Instant plannedEnd,
            int toleranceHours, UUID responsibleUserId, boolean dependsOnPrevious, ScheduleStepStatus status,
            Instant executedAt, String justification) {
        this.id = Objects.requireNonNull(id, "id");
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequência deve ser positiva");
        }
        this.sequence = sequence;
        this.name = requireText(name, "nome da etapa");
        this.action = Objects.requireNonNull(action, "ação é obrigatória");
        this.condition = Objects.requireNonNull(condition, "condição é obrigatória");
        this.plannedStart = Objects.requireNonNull(plannedStart, "início planejado é obrigatório");
        this.plannedEnd = Objects.requireNonNull(plannedEnd, "fim planejado é obrigatório");
        if (plannedEnd.isBefore(plannedStart)) {
            throw new IllegalArgumentException("janela planejada termina antes de começar");
        }
        if (toleranceHours < 0) {
            throw new IllegalArgumentException("tolerância não pode ser negativa");
        }
        this.toleranceHours = toleranceHours;
        this.responsibleUserId = Objects.requireNonNull(responsibleUserId, "responsável é obrigatório");
        this.dependsOnPrevious = dependsOnPrevious;
        this.status = Objects.requireNonNull(status, "status");
        this.executedAt = executedAt;
        this.justification = justification;
        validateCondition(condition, conditionDays, targetGravity);
        this.conditionDays = conditionDays;
        this.targetGravity = targetGravity;
    }

    public static ScheduleStep plan(int sequence, String name, ScheduleAction action, AdvanceCondition condition,
            Integer conditionDays, BigDecimal targetGravity, Instant plannedStart, Instant plannedEnd,
            int toleranceHours, UUID responsibleUserId, boolean dependsOnPrevious) {
        return new ScheduleStep(UUID.randomUUID(), sequence, name, action, condition, conditionDays, targetGravity,
                plannedStart, plannedEnd, toleranceHours, responsibleUserId, dependsOnPrevious,
                ScheduleStepStatus.PLANNED, null, null);
    }

    public static ScheduleStep reconstitute(UUID id, int sequence, String name, ScheduleAction action,
            AdvanceCondition condition, Integer conditionDays, BigDecimal targetGravity, Instant plannedStart,
            Instant plannedEnd, int toleranceHours, UUID responsibleUserId, boolean dependsOnPrevious,
            ScheduleStepStatus status, Instant executedAt, String justification) {
        return new ScheduleStep(id, sequence, name, action, condition, conditionDays, targetGravity, plannedStart,
                plannedEnd, toleranceHours, responsibleUserId, dependsOnPrevious, status, executedAt, justification);
    }

    /**
     * Registra a execução. O planejado permanece; o desvio passa a ser legível pela diferença
     * entre {@code executedAt} e a janela. Fora da tolerância, a justificativa é obrigatória.
     */
    public void execute(Instant at, String justification) {
        if (status.done()) {
            throw new IllegalStateException("etapa já executada");
        }
        Objects.requireNonNull(at, "instante da execução é obrigatório");
        var deviation = deviationHoursAt(at);
        if (Math.abs(deviation) > toleranceHours && (justification == null || justification.isBlank())) {
            throw new IllegalArgumentException(
                    "execução fora da tolerância (" + deviation + "h) exige justificativa");
        }
        this.executedAt = at;
        this.justification = justification == null || justification.isBlank() ? null : justification.trim();
        this.status = ScheduleStepStatus.DONE;
    }

    /** Desloca a janela planejada; etapa executada nunca se move. */
    void shift(Duration by) {
        if (status.done()) {
            throw new IllegalStateException("etapa executada não é replanejada");
        }
        this.plannedStart = plannedStart.plus(by);
        this.plannedEnd = plannedEnd.plus(by);
    }

    /** Move a janela para um novo início, preservando a duração. */
    Duration moveStartTo(Instant newStart) {
        var delta = Duration.between(plannedStart, newStart);
        shift(delta);
        return delta;
    }

    /** Horas de desvio em relação à janela: negativo antes, positivo depois, zero dentro. */
    public long deviationHoursAt(Instant at) {
        if (at.isBefore(plannedStart)) {
            return -Duration.between(at, plannedStart).toHours();
        }
        if (at.isAfter(plannedEnd)) {
            return Duration.between(plannedEnd, at).toHours();
        }
        return 0;
    }

    /** Desvio efetivo da execução; zero enquanto pendente. */
    public long deviationHours() {
        return executedAt == null ? 0 : deviationHoursAt(executedAt);
    }

    /** Pendente e com a janela já vencida além da tolerância. */
    public boolean lateAt(Instant now) {
        return !status.done() && now.isAfter(plannedEnd.plus(Duration.ofHours(toleranceHours)));
    }

    public Duration duration() {
        return Duration.between(plannedStart, plannedEnd);
    }

    private static void validateCondition(AdvanceCondition condition, Integer days, BigDecimal gravity) {
        switch (condition) {
            case TIME -> {
                if (days == null || days <= 0) {
                    throw new IllegalArgumentException("avanço por tempo exige dias positivos");
                }
                if (gravity != null) {
                    throw new IllegalArgumentException("avanço por tempo não usa densidade-alvo");
                }
            }
            case GRAVITY -> {
                if (gravity == null || gravity.signum() <= 0) {
                    throw new IllegalArgumentException("avanço por densidade exige FG-alvo positivo");
                }
                if (days != null) {
                    throw new IllegalArgumentException("avanço por densidade não usa dias");
                }
            }
            case MANUAL -> {
                if (days != null || gravity != null) {
                    throw new IllegalArgumentException("avanço manual não usa dias nem densidade-alvo");
                }
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException(field + " excede 120 caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public int sequence() { return sequence; }
    public String name() { return name; }
    public ScheduleAction action() { return action; }
    public AdvanceCondition condition() { return condition; }
    public Integer conditionDays() { return conditionDays; }
    public BigDecimal targetGravity() { return targetGravity; }
    public Instant plannedStart() { return plannedStart; }
    public Instant plannedEnd() { return plannedEnd; }
    public int toleranceHours() { return toleranceHours; }
    public UUID responsibleUserId() { return responsibleUserId; }
    public boolean dependsOnPrevious() { return dependsOnPrevious; }
    public ScheduleStepStatus status() { return status; }
    public Instant executedAt() { return executedAt; }
    public String justification() { return justification; }
}
