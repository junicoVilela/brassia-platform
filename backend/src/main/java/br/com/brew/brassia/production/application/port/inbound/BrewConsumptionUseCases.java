package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.ProductionStockGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Consumo de insumo do dia de brassa (TRC-001-C): o que a OP separou virando o que a brassagem
 * usou.
 */
public interface BrewConsumptionUseCases {

    interface Proposal {
        /**
         * O que a OP tem reservado, para o operador confirmar ou corrigir.
         *
         * <p>Propor a reserva é o que torna o registro barato no caso comum — na maioria dos dias a
         * brassagem usa o que foi separado. O que não pode é o sistema <em>assumir</em> a proposta:
         * quem trocou de lote precisa poder dizer isso, e é essa divergência que o registro existe
         * para capturar.
         */
        Result handle(UUID breweryId, UUID batchId);

        record Result(UUID orderId, boolean alreadyRegistered,
                List<ProductionStockGateway.ReservedLot> reserved) {}
    }

    interface Register {
        void handle(Command command);

        record Command(UUID breweryId, UUID actorId, UUID batchId, List<Line> lines) {}

        record Line(UUID lotId, BigDecimal quantity, String unit) {}
    }
}
