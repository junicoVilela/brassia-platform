package br.com.brew.brassia.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Desvio aberto por uma medição fora da faixa (QLT-001).
 *
 * <p>Nasce com a severidade do <em>ponto</em>, não da medição, e carrega o limite rompido e a ação
 * que o plano manda tomar. Sem esses três juntos o desvio seria só um aviso de que algo saiu do
 * lugar, sem dizer o quanto importa nem o que fazer.
 */
public final class Deviation {

    private final UUID id;
    private final UUID breweryId;
    private final UUID measurementId;
    private final UUID planId;
    private final UUID pointId;
    private final String parameter;
    private final Severity severity;
    private final SpecLimits.Bound bound;
    private final BigDecimal limitValue;
    private final BigDecimal measuredValue;
    private final String unit;
    private final String action;
    private DeviationStatus status;
    private final Instant openedAt;
    private final UUID openedBy;

    private Deviation(UUID id, UUID breweryId, UUID measurementId, UUID planId, UUID pointId, String parameter,
            Severity severity, SpecLimits.Bound bound, BigDecimal limitValue, BigDecimal measuredValue,
            String unit, String action, DeviationStatus status, Instant openedAt, UUID openedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.measurementId = Objects.requireNonNull(measurementId, "measurementId");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.pointId = Objects.requireNonNull(pointId, "pointId");
        this.parameter = Objects.requireNonNull(parameter, "parâmetro");
        this.severity = Objects.requireNonNull(severity, "severidade");
        this.bound = Objects.requireNonNull(bound, "limite rompido");
        this.limitValue = Objects.requireNonNull(limitValue, "valor do limite");
        this.measuredValue = Objects.requireNonNull(measuredValue, "valor medido");
        this.unit = Objects.requireNonNull(unit, "unidade");
        this.action = Objects.requireNonNull(action, "ação");
        this.status = Objects.requireNonNull(status, "situação");
        this.openedAt = Objects.requireNonNull(openedAt, "instante");
        this.openedBy = Objects.requireNonNull(openedBy, "responsável");
    }

    public static Deviation open(UUID breweryId, UUID measurementId, ControlPlan plan, ControlPoint point,
            SpecLimits.Violation violation, BigDecimal measuredValue, Instant openedAt, UUID openedBy) {
        return new Deviation(UUID.randomUUID(), breweryId, measurementId, plan.id(), point.id(),
                point.parameter(), point.severity(), violation.bound(), violation.limit(), measuredValue,
                point.limits().unit(), point.action(), DeviationStatus.OPEN, openedAt, openedBy);
    }

    public static Deviation reconstitute(UUID id, UUID breweryId, UUID measurementId, UUID planId,
            UUID pointId, String parameter, Severity severity, SpecLimits.Bound bound, BigDecimal limitValue,
            BigDecimal measuredValue, String unit, String action, DeviationStatus status, Instant openedAt,
            UUID openedBy) {
        return new Deviation(id, breweryId, measurementId, planId, pointId, parameter, severity, bound,
                limitValue, measuredValue, unit, action, status, openedAt, openedBy);
    }

    /**
     * Encerra o desvio. Só a não conformidade fecha (QLT-002), e só depois de uma verificação de
     * eficácia bem-sucedida: um desvio que se fecha sozinho seria um problema que sumiu do painel
     * sem que nada tenha sido feito.
     */
    public void close() {
        if (status == DeviationStatus.CLOSED) {
            throw new IllegalStateException("desvio já encerrado");
        }
        this.status = DeviationStatus.CLOSED;
    }

    /** O quanto passou do limite — o tamanho do problema, não só a sua existência. */
    public BigDecimal excess() {
        return bound == SpecLimits.Bound.ABOVE_MAX
                ? measuredValue.subtract(limitValue)
                : limitValue.subtract(measuredValue);
    }

    public String describe() {
        return "%s medido em %s %s, %s o limite de %s %s".formatted(parameter,
                measuredValue.toPlainString(), unit,
                bound == SpecLimits.Bound.ABOVE_MAX ? "acima d" : "abaixo d",
                limitValue.toPlainString(), unit);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID measurementId() {
        return measurementId;
    }

    public UUID planId() {
        return planId;
    }

    public UUID pointId() {
        return pointId;
    }

    public String parameter() {
        return parameter;
    }

    public Severity severity() {
        return severity;
    }

    public SpecLimits.Bound bound() {
        return bound;
    }

    public BigDecimal limitValue() {
        return limitValue;
    }

    public BigDecimal measuredValue() {
        return measuredValue;
    }

    public String unit() {
        return unit;
    }

    public String action() {
        return action;
    }

    public DeviationStatus status() {
        return status;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public UUID openedBy() {
        return openedBy;
    }
}
