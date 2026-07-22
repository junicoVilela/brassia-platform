package br.com.brew.brassia.equipment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Snapshot imutável de uma versão do perfil de equipamento (histórico). */
public record EquipmentSnapshot(
        UUID equipmentId,
        UUID breweryId,
        String code,
        String name,
        BigDecimal capacityLiters,
        BigDecimal deadSpaceLiters,
        BigDecimal mashEfficiencyPercent,
        BigDecimal boilOffLitersPerHour,
        long version) {}
