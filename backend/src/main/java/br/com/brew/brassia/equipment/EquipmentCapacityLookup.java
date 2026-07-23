package br.com.brew.brassia.equipment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada do módulo de equipamentos: capacidade (litros) de um
 * equipamento de uma cervejaria. Permite que outros módulos (ex.: receitas)
 * validem volume contra capacidade sem acessar a tabela de equipamentos.
 */
public interface EquipmentCapacityLookup {
    Optional<BigDecimal> capacityLiters(UUID breweryId, UUID equipmentId);
}
