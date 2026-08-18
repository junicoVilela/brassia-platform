package br.com.brew.brassia.equipment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * O equipamento como os outros módulos precisam vê-lo: o código que a casa usa e a capacidade.
 *
 * <p><strong>Por que não bastava o {@link EquipmentCapacityLookup}.</strong> Aquele responde litros, e é
 * uma interface funcional — quem precisa também do código não tinha por onde perguntar sem ler a tabela
 * de equipamentos. O código importa porque é o que aparece na tela: uma capacidade explicada por
 * "FV-01 e FV-02" se confere; a mesma explicada por dois UUIDs, não.
 */
public interface EquipmentSummaryLookup {

    Optional<Summary> find(UUID breweryId, UUID equipmentId);

    record Summary(UUID id, String code, BigDecimal capacityLiters) {}
}
