package br.com.brew.brassia.equipment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada do perfil de equipamento (EQP-001), para outros módulos
 * calcularem sobre capacidade, perdas e evaporação sem acessar a tabela.
 */
public interface EquipmentProfileLookup {
    Optional<Profile> find(UUID breweryId, UUID equipmentId);

    record Profile(BigDecimal capacityLiters, BigDecimal deadSpaceLiters, BigDecimal mashEfficiencyPercent,
            BigDecimal boilOffLitersPerHour) {}
}
