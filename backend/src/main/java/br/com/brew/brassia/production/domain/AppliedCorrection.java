package br.com.brew.brassia.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Correção aplicada no dia de brassa (CAL-002): a decisão registrada de uma
 * correção determinística. Preserva a medição de origem, a hipótese (inputs), o
 * efeito estimado ({@code planejado}) e, opcionalmente, o valor {@code realizado}.
 * Nenhuma ação física é executada — é o registro da decisão + evento.
 */
public final class AppliedCorrection {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final String calculator;
    private final UUID sourceMeasurementId;
    private final String note;
    private final Map<String, BigDecimal> inputs;
    private final BigDecimal plannedValue;
    private final String plannedUnit;
    private final BigDecimal realizedValue;
    private final Instant appliedAt;
    private final UUID appliedBy;

    private AppliedCorrection(UUID id, UUID breweryId, UUID batchId, String calculator, UUID sourceMeasurementId,
            String note, Map<String, BigDecimal> inputs, BigDecimal plannedValue, String plannedUnit,
            BigDecimal realizedValue, Instant appliedAt, UUID appliedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.calculator = requireText(calculator, "calculadora");
        this.sourceMeasurementId = sourceMeasurementId;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.inputs = new LinkedHashMap<>(Objects.requireNonNull(inputs, "inputs"));
        this.plannedValue = Objects.requireNonNull(plannedValue, "plannedValue");
        this.plannedUnit = requireText(plannedUnit, "unidade do planejado");
        this.realizedValue = realizedValue;
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
        this.appliedBy = Objects.requireNonNull(appliedBy, "appliedBy");
    }

    public static AppliedCorrection record(UUID breweryId, UUID batchId, String calculator, UUID sourceMeasurementId,
            String note, Map<String, BigDecimal> inputs, BigDecimal plannedValue, String plannedUnit,
            BigDecimal realizedValue, Instant appliedAt, UUID appliedBy) {
        return new AppliedCorrection(UUID.randomUUID(), breweryId, batchId, calculator, sourceMeasurementId, note,
                inputs, plannedValue, plannedUnit, realizedValue, appliedAt, appliedBy);
    }

    public static AppliedCorrection reconstitute(UUID id, UUID breweryId, UUID batchId, String calculator,
            UUID sourceMeasurementId, String note, Map<String, BigDecimal> inputs, BigDecimal plannedValue,
            String plannedUnit, BigDecimal realizedValue, Instant appliedAt, UUID appliedBy) {
        return new AppliedCorrection(id, breweryId, batchId, calculator, sourceMeasurementId, note, inputs,
                plannedValue, plannedUnit, realizedValue, appliedAt, appliedBy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public String calculator() { return calculator; }
    public UUID sourceMeasurementId() { return sourceMeasurementId; }
    public String note() { return note; }
    public Map<String, BigDecimal> inputs() { return Map.copyOf(inputs); }
    public BigDecimal plannedValue() { return plannedValue; }
    public String plannedUnit() { return plannedUnit; }
    public BigDecimal realizedValue() { return realizedValue; }
    public Instant appliedAt() { return appliedAt; }
    public UUID appliedBy() { return appliedBy; }
}
