package br.com.brew.brassia.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Medição de um parâmetro contra um ponto do plano (QLT-001).
 *
 * <p>Guarda a <strong>versão do plano</strong> pela qual foi julgada: é o que permite dizer, meses
 * depois, contra qual faixa aquele número foi aprovado. E guarda a aptidão do instrumento no
 * momento — em ponto não crítico a medição passa mesmo com instrumento vencido, mas passa
 * <em>dizendo</em> que passou assim.
 */
public final class Measurement {

    private final UUID id;
    private final UUID breweryId;
    private final UUID planId;
    private final int planVersion;
    private final UUID pointId;
    private final String parameter;
    private final UUID batchId;
    private final UUID instrumentId;
    private final String instrumentFitness;
    private final BigDecimal value;
    private final String unit;
    private final boolean withinSpec;
    private final String note;
    private final Instant measuredAt;
    private final UUID measuredBy;

    private Measurement(UUID id, UUID breweryId, UUID planId, int planVersion, UUID pointId, String parameter,
            UUID batchId, UUID instrumentId, String instrumentFitness, BigDecimal value, String unit,
            boolean withinSpec, String note, Instant measuredAt, UUID measuredBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.planVersion = planVersion;
        this.pointId = Objects.requireNonNull(pointId, "pointId");
        this.parameter = Objects.requireNonNull(parameter, "parâmetro");
        this.batchId = batchId;
        this.instrumentId = instrumentId;
        this.instrumentFitness = instrumentFitness;
        this.value = Objects.requireNonNull(value, "valor medido é obrigatório");
        this.unit = Objects.requireNonNull(unit, "unidade");
        this.withinSpec = withinSpec;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.measuredAt = Objects.requireNonNull(measuredAt, "instante é obrigatório");
        this.measuredBy = Objects.requireNonNull(measuredBy, "responsável");
    }

    public static Measurement record(UUID breweryId, ControlPlan plan, ControlPoint point, UUID batchId,
            UUID instrumentId, String instrumentFitness, BigDecimal value, boolean withinSpec, String note,
            Instant measuredAt, UUID measuredBy) {
        return new Measurement(UUID.randomUUID(), breweryId, plan.id(), plan.version(), point.id(),
                point.parameter(), batchId, instrumentId, instrumentFitness, value, point.limits().unit(),
                withinSpec, note, measuredAt, measuredBy);
    }

    public static Measurement reconstitute(UUID id, UUID breweryId, UUID planId, int planVersion, UUID pointId,
            String parameter, UUID batchId, UUID instrumentId, String instrumentFitness, BigDecimal value,
            String unit, boolean withinSpec, String note, Instant measuredAt, UUID measuredBy) {
        return new Measurement(id, breweryId, planId, planVersion, pointId, parameter, batchId, instrumentId,
                instrumentFitness, value, unit, withinSpec, note, measuredAt, measuredBy);
    }

    /** Medição feita com instrumento que não estava apto: vale, mas com a evidência enfraquecida. */
    public boolean instrumentQuestionable() {
        return instrumentFitness != null && !"FIT".equals(instrumentFitness);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID planId() {
        return planId;
    }

    public int planVersion() {
        return planVersion;
    }

    public UUID pointId() {
        return pointId;
    }

    public String parameter() {
        return parameter;
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID instrumentId() {
        return instrumentId;
    }

    public String instrumentFitness() {
        return instrumentFitness;
    }

    public BigDecimal value() {
        return value;
    }

    public String unit() {
        return unit;
    }

    public boolean withinSpec() {
        return withinSpec;
    }

    public String note() {
        return note;
    }

    public Instant measuredAt() {
        return measuredAt;
    }

    public UUID measuredBy() {
        return measuredBy;
    }
}
