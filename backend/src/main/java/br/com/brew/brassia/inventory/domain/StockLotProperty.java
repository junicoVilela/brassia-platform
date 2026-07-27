package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Valor medido específico de um lote (STK-005): alfa-ácido, extrato, umidade,
 * células, etc. Pertence ao lote (não ao catálogo) e registra fonte e confiança
 * do vínculo. Uma vez gravado, é imutável — evidência do que foi recebido.
 */
public final class StockLotProperty {

    private final UUID id;
    private final UUID lotId;
    private final UUID breweryId;
    private final String property;
    private final BigDecimal measuredValue;
    private final String unit;
    private final LotPropertySource source;
    private final LotPropertyConfidence confidence;
    private final Instant recordedAt;
    private final UUID recordedBy;

    private StockLotProperty(UUID id, UUID lotId, UUID breweryId, String property, BigDecimal measuredValue,
            String unit, LotPropertySource source, LotPropertyConfidence confidence, Instant recordedAt,
            UUID recordedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.lotId = Objects.requireNonNull(lotId, "lotId");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.property = requireProperty(property);
        this.measuredValue = Objects.requireNonNull(measuredValue, "measuredValue");
        this.unit = unit == null || unit.isBlank() ? null : unit.trim();
        this.source = Objects.requireNonNull(source, "source");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy");
    }

    public static StockLotProperty record(UUID lotId, UUID breweryId, String property, BigDecimal measuredValue,
            String unit, LotPropertySource source, LotPropertyConfidence confidence, Instant recordedAt,
            UUID recordedBy) {
        return new StockLotProperty(UUID.randomUUID(), lotId, breweryId, property, measuredValue, unit, source,
                confidence, recordedAt, recordedBy);
    }

    public static StockLotProperty reconstitute(UUID id, UUID lotId, UUID breweryId, String property,
            BigDecimal measuredValue, String unit, LotPropertySource source, LotPropertyConfidence confidence,
            Instant recordedAt, UUID recordedBy) {
        return new StockLotProperty(id, lotId, breweryId, property, measuredValue, unit, source, confidence,
                recordedAt, recordedBy);
    }

    private static String requireProperty(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("propriedade obrigatória");
        }
        var trimmed = value.trim();
        if (trimmed.length() > 60) {
            throw new IllegalArgumentException("propriedade excede 60 caracteres");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID lotId() {
        return lotId;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String property() {
        return property;
    }

    public BigDecimal measuredValue() {
        return measuredValue;
    }

    public String unit() {
        return unit;
    }

    public LotPropertySource source() {
        return source;
    }

    public LotPropertyConfidence confidence() {
        return confidence;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public UUID recordedBy() {
        return recordedBy;
    }
}
