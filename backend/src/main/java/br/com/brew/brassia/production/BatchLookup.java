package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada do lote (PRD-001), para outros módulos vincularem dados ao lote sem
 * acessar a tabela de produção (ex.: leituras de fermentação, FER-002; plano de envase, PKG-001).
 */
public interface BatchLookup {

    Optional<Snapshot> find(UUID breweryId, UUID batchId);

    default boolean exists(UUID breweryId, UUID batchId) {
        return find(breweryId, batchId).isPresent();
    }

    /**
     * Identificação, volume e estado do lote; {@code status} é o nome do estado de produção.
     *
     * @param volumeLiters            volume planejado do lote, vindo da ordem
     * @param packageableVolumeLiters cerveja que existe de fato para envasar: o volume transferido
     *                                ao fermentador quando já houve transferência, senão o planejado.
     *                                São diferentes porque a transferência tem perdas, e envasar
     *                                contra o planejado inventaria cerveja que não está no tanque.
     */
    record Snapshot(UUID batchId, String code, BigDecimal volumeLiters, BigDecimal packageableVolumeLiters,
            String status, UUID recipeId, int recipeVersion, String recipeName) {}
}
