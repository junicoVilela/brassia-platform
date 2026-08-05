package br.com.brew.brassia.costing.application.port.inbound;

import br.com.brew.brassia.costing.domain.BatchCost;
import java.util.UUID;

/** Comandos do custo (CST-001). */
public interface CostCommands {

    interface Close {
        /**
         * Fecha o custo do lote com o que está somado agora.
         *
         * <p>É um ato com autor: alguém olha o número, aceita as lacunas declaradas e assina.
         * Terminar de produzir e terminar de apurar são coisas diferentes.
         */
        BatchCost handle(UUID actorId, UUID breweryId, UUID batchId, String note);
    }
}
