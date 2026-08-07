package br.com.brew.brassia.packaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que cada envase do lote planejou e o que ele entregou (CST-002).
 *
 * <p>Só execuções entram: plano sem execução é intenção, e comparar intenção com intenção não
 * explica variação nenhuma. O lote pode ter mais de um envase — lata e barril no mesmo lote —, e
 * cada um se explica sozinho, porque a linha que rejeitou 5% de latas não tem nada a ver com o
 * barril que saiu redondo.
 */
public interface PackagingOutcomeLookup {

    List<PackagingOutcome> outcomesOfBatch(UUID breweryId, UUID batchId);

    /**
     * @param plannedVolumeLiters  volume que o plano previa envasar
     * @param packagedVolumeLiters volume que virou produto
     * @param rejectedVolumeLiters volume que foi envasado e descartado — cerveja perdida depois de
     *                             pronta, que é a perda mais cara que existe
     * @param lossesLiters         o que entrou na linha e não saiu nem como produto nem como
     *                             rejeito, derivado do balanço do envase
     */
    record PackagingOutcome(String planCode, BigDecimal plannedVolumeLiters,
            BigDecimal packagedVolumeLiters, BigDecimal rejectedVolumeLiters, BigDecimal lossesLiters) {}
}
