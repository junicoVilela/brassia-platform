package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * As horas trabalhadas num lote, publicadas para quem precisa custeá-las (CST-001-A).
 *
 * <p>Devolve <strong>hora, nunca dinheiro</strong>: quanto vale a hora é decisão de gestão, e mora no
 * custeio. A produção responde pelo que sabe — quem trabalhou, quando e quanto tempo.
 */
public interface LaborLookup {

    /** Apontamentos do lote, do mais antigo para o mais novo. */
    List<LaborTime> ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param manHours horas-homem: duração × pessoas. Duas pessoas por três horas são seis, e é isso que
     *                 a cervejaria paga
     */
    record LaborTime(String activity, BigDecimal manHours) {}
}
