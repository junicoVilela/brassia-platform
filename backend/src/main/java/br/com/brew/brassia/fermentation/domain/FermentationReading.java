package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Leitura de fermentação (FER-002): valor de uma grandeza para um lote, num instante, de
 * origem manual ou sensor. A validade é computada pela plausibilidade da grandeza/unidade —
 * fora da faixa é gravada com {@code valid=false} e um motivo (sinalizada, não rejeitada).
 */
public final class FermentationReading {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final ReadingKind kind;
    private final ReadingSource source;
    private final BigDecimal value;
    private final String unit;
    private final Instant measuredAt;
    private final boolean valid;
    private final String invalidReason;

    private FermentationReading(UUID id, UUID breweryId, UUID batchId, ReadingKind kind, ReadingSource source,
            BigDecimal value, String unit, Instant measuredAt, boolean valid, String invalidReason) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.source = Objects.requireNonNull(source, "source");
        this.value = Objects.requireNonNull(value, "value");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.measuredAt = Objects.requireNonNull(measuredAt, "measuredAt");
        this.valid = valid;
        this.invalidReason = invalidReason;
    }

    /** Registra uma leitura, computando validade pela plausibilidade da grandeza/unidade. */
    public static FermentationReading record(UUID breweryId, UUID batchId, ReadingKind kind, ReadingSource source,
            BigDecimal value, String rawUnit, Instant measuredAt) {
        Objects.requireNonNull(value, "valor é obrigatório");
        Objects.requireNonNull(measuredAt, "instante é obrigatório");
        var unit = kind.requireUnit(rawUnit);
        var reason = kind.implausibleReason(value, unit);
        return new FermentationReading(UUID.randomUUID(), breweryId, batchId, kind, source, value, unit, measuredAt,
                reason == null, reason);
    }

    public static FermentationReading reconstitute(UUID id, UUID breweryId, UUID batchId, ReadingKind kind,
            ReadingSource source, BigDecimal value, String unit, Instant measuredAt, boolean valid,
            String invalidReason) {
        return new FermentationReading(id, breweryId, batchId, kind, source, value, unit, measuredAt, valid,
                invalidReason);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public ReadingKind kind() { return kind; }
    public ReadingSource source() { return source; }
    public BigDecimal value() { return value; }
    public String unit() { return unit; }
    public Instant measuredAt() { return measuredAt; }
    public boolean valid() { return valid; }
    public String invalidReason() { return invalidReason; }
}
