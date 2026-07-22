package br.com.brew.brassia.equipment.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil de equipamento (EQP-001): capacidade, perdas (dead space), eficiência
 * de mostura e evaporação. Invariante central: o volume de perda não pode
 * exceder a capacidade. Multi-tenant por {@code breweryId}; cada versão é
 * preservada como snapshot (histórico).
 */
public final class Equipment {
    private final EquipmentId id;
    private final UUID breweryId;
    private final EquipmentCode code;
    private EquipmentName name;
    private BigDecimal capacityLiters;
    private BigDecimal deadSpaceLiters;
    private BigDecimal mashEfficiencyPercent;
    private BigDecimal boilOffLitersPerHour;
    private final boolean active;
    private final long version;

    private Equipment(EquipmentId id, UUID breweryId, EquipmentCode code, EquipmentName name,
            BigDecimal capacityLiters, BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent,
            BigDecimal boilOffLitersPerHour, boolean active, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = Objects.requireNonNull(code);
        applyMeasures(name, capacityLiters, deadSpaceLiters, mashEfficiencyPercent, boilOffLitersPerHour);
        this.active = active;
        this.version = version;
    }

    public static Equipment register(UUID breweryId, EquipmentCode code, EquipmentName name,
            BigDecimal capacityLiters, BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent,
            BigDecimal boilOffLitersPerHour) {
        return new Equipment(EquipmentId.newId(), breweryId, code, name, capacityLiters, deadSpaceLiters,
                mashEfficiencyPercent, boilOffLitersPerHour, true, 0);
    }

    public static Equipment reconstitute(EquipmentId id, UUID breweryId, EquipmentCode code, EquipmentName name,
            BigDecimal capacityLiters, BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent,
            BigDecimal boilOffLitersPerHour, boolean active, long version) {
        return new Equipment(id, breweryId, code, name, capacityLiters, deadSpaceLiters, mashEfficiencyPercent,
                boilOffLitersPerHour, active, version);
    }

    /** Atualiza medidas mutáveis; código é imutável. A versão é controlada na borda de persistência. */
    public void update(EquipmentName name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour) {
        applyMeasures(name, capacityLiters, deadSpaceLiters, mashEfficiencyPercent, boilOffLitersPerHour);
    }

    private void applyMeasures(EquipmentName name, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour) {
        this.name = Objects.requireNonNull(name);
        this.capacityLiters = requirePositive(capacityLiters, "capacidade");
        this.deadSpaceLiters = requireNonNegative(deadSpaceLiters, "dead space");
        this.mashEfficiencyPercent = requirePercentage(mashEfficiencyPercent);
        this.boilOffLitersPerHour = requireNonNegative(boilOffLitersPerHour, "evaporação");
        if (this.deadSpaceLiters.compareTo(this.capacityLiters) > 0) {
            throw new IllegalArgumentException("volume de perda não pode exceder a capacidade");
        }
    }

    public EquipmentSnapshot snapshot() {
        return new EquipmentSnapshot(id.value(), breweryId, code.value(), name.value(), capacityLiters,
                deadSpaceLiters, mashEfficiencyPercent, boilOffLitersPerHour, version);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positiva");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    private static BigDecimal requirePercentage(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("eficiência deve estar entre 0 (exclusivo) e 100");
        }
        return value;
    }

    public EquipmentId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public EquipmentCode code() { return code; }
    public EquipmentName name() { return name; }
    public BigDecimal capacityLiters() { return capacityLiters; }
    public BigDecimal deadSpaceLiters() { return deadSpaceLiters; }
    public BigDecimal mashEfficiencyPercent() { return mashEfficiencyPercent; }
    public BigDecimal boilOffLitersPerHour() { return boilOffLitersPerHour; }
    public boolean active() { return active; }
    public long version() { return version; }
}
