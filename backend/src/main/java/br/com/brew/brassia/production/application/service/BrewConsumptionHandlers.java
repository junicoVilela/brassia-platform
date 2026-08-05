package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.ProductionStockGateway;
import br.com.brew.brassia.production.application.port.inbound.BrewConsumptionUseCases;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BrewConsumptionException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra o que a brassagem consumiu, por lote de insumo (TRC-001-C).
 *
 * <p>Fecha a lacuna aberta na TRC-001: até aqui, a única ligação entre insumo e lote de produção
 * era a reserva da OP — intenção, não fato. Num recall, tratar intenção como fato recolhe o lote
 * errado; num custo realizado (CST-001), soma o preço do lote errado. Confirmar o consumo é o que
 * transforma a aresta {@code INTENDED} da genealogia em {@code CONFIRMED}, sem que uma linha da
 * rastreabilidade mude.
 */
public final class BrewConsumptionHandlers {

    private BrewConsumptionHandlers() {
    }

    public static final class Proposal implements BrewConsumptionUseCases.Proposal {

        private final BatchRepository batches;
        private final ProductionStockGateway stock;

        public Proposal(BatchRepository batches, ProductionStockGateway stock) {
            this.batches = Objects.requireNonNull(batches);
            this.stock = Objects.requireNonNull(stock);
        }

        @Override
        public Result handle(UUID breweryId, UUID batchId) {
            var batch = batch(batches, breweryId, batchId);
            return new Result(batch.orderId(), stock.alreadyConsumed(breweryId, batch.orderId()),
                    stock.reservedFor(breweryId, batch.orderId()));
        }
    }

    public static final class Register implements BrewConsumptionUseCases.Register {

        private final BatchRepository batches;
        private final ProductionStockGateway stock;
        private final AuditTrail audit;

        public Register(BatchRepository batches, ProductionStockGateway stock, AuditTrail audit) {
            this.batches = Objects.requireNonNull(batches);
            this.stock = Objects.requireNonNull(stock);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var batch = batch(batches, command.breweryId(), command.batchId());
            // Lote encerrado ou cancelado não recebe consumo novo; em fermentação ainda recebe,
            // porque quem esqueceu de registrar no dia consegue corrigir no seguinte — registrar
            // tarde é melhor do que nunca, e o movimento carrega a própria data.
            if (batch.status() == BatchStatus.COMPLETED || batch.status() == BatchStatus.CANCELLED) {
                throw new IllegalStateException("lote " + batch.status() + " não recebe consumo");
            }
            if (command.lines() == null || command.lines().isEmpty()) {
                throw new IllegalArgumentException("declare ao menos um lote de insumo consumido");
            }
            if (stock.alreadyConsumed(command.breweryId(), batch.orderId())) {
                // Lançar duas vezes dobraria o consumo e o custo. Corrigir um consumo errado é
                // assunto de ajuste de estoque, que tem comando próprio e deixa rastro próprio.
                throw new IllegalStateException("o consumo desta brassagem já foi registrado");
            }

            var lines = command.lines().stream()
                    .map(line -> new ProductionStockGateway.ConsumedLot(line.lotId(), line.quantity(),
                            line.unit()))
                    .toList();
            var outcome = stock.consume(command.breweryId(), batch.orderId(), command.actorId(), lines);
            if (!outcome.consumed()) {
                throw new BrewConsumptionException(outcome.shortfalls());
            }

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "production.brew.consumption", "production.batch", batch.id().value().toString(),
                    Map.of("code", batch.code(), "orderId", batch.orderId().toString(),
                            "lots", String.valueOf(lines.size()))));
        }
    }

    private static Batch batch(BatchRepository batches, UUID breweryId, UUID batchId) {
        return batches.findById(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
    }
}
