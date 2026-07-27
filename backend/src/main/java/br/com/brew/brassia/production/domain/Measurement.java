package br.com.brew.brassia.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Medição registrada no dia de brassa (PRD-003): valor + unidade (compatível com
 * a grandeza), temperatura, método, origem e operador. Imutável — evidência do
 * que foi medido. Pode referenciar uma etapa do roteiro (contexto).
 */
public final class Measurement {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final UUID stepId;
    private final MeasurementKind kind;
    private final BigDecimal value;
    private final String unit;
    private final BigDecimal temperatureC;
    private final String method;
    private final MeasurementSource source;
    private final Instant recordedAt;
    private final UUID recordedBy;

    private Measurement(UUID id, UUID breweryId, UUID batchId, UUID stepId, MeasurementKind kind, BigDecimal value,
            String unit, BigDecimal temperatureC, String method, MeasurementSource source, Instant recordedAt,
            UUID recordedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.stepId = stepId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = Objects.requireNonNull(value, "value");
        this.unit = kind.requireUnit(unit); // valida compatibilidade grandeza × unidade
        this.temperatureC = temperatureC;
        this.method = method == null || method.isBlank() ? null : method.trim();
        this.source = Objects.requireNonNull(source, "source");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy");
    }

    public static Measurement record(UUID breweryId, UUID batchId, UUID stepId, MeasurementKind kind,
            BigDecimal value, String unit, BigDecimal temperatureC, String method, MeasurementSource source,
            Instant recordedAt, UUID recordedBy) {
        return new Measurement(UUID.randomUUID(), breweryId, batchId, stepId, kind, value, unit, temperatureC,
                method, source, recordedAt, recordedBy);
    }

    public static Measurement reconstitute(UUID id, UUID breweryId, UUID batchId, UUID stepId,
            MeasurementKind kind, BigDecimal value, String unit, BigDecimal temperatureC, String method,
            MeasurementSource source, Instant recordedAt, UUID recordedBy) {
        return new Measurement(id, breweryId, batchId, stepId, kind, value, unit, temperatureC, method, source,
                recordedAt, recordedBy);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public UUID stepId() { return stepId; }
    public MeasurementKind kind() { return kind; }
    public BigDecimal value() { return value; }
    public String unit() { return unit; }
    public BigDecimal temperatureC() { return temperatureC; }
    public String method() { return method; }
    public MeasurementSource source() { return source; }
    public Instant recordedAt() { return recordedAt; }
    public UUID recordedBy() { return recordedBy; }
}
