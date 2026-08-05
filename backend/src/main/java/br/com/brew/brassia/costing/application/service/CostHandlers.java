package br.com.brew.brassia.costing.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.application.port.inbound.CostCommands;
import br.com.brew.brassia.costing.application.port.inbound.CostQueries;
import br.com.brew.brassia.costing.application.port.outbound.BatchCostRepository;
import br.com.brew.brassia.costing.domain.BatchCost;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta e fechamento do custo do lote (CST-001).
 *
 * <p>A consulta responde o custo fechado quando existe e o custo de agora quando não existe. Não é
 * um detalhe de implementação: é a regra. Enquanto ninguém fechou, o número ainda muda porque a
 * produção ainda muda; depois de fechado, ele é evidência.
 */
public final class CostHandlers {

    private CostHandlers() {
    }

    public static final class Queries implements CostQueries {

        private final BatchCostRepository costs;
        private final BatchCostAssembler assembler;

        public Queries(BatchCostRepository costs, BatchCostAssembler assembler) {
            this.costs = Objects.requireNonNull(costs);
            this.assembler = Objects.requireNonNull(assembler);
        }

        @Override
        public List<BatchCost> closed(UUID breweryId) {
            return costs.findAll(breweryId);
        }

        @Override
        public BatchCost ofBatch(UUID breweryId, UUID batchId) {
            return costs.findByBatch(breweryId, batchId)
                    .orElseGet(() -> assembler.assemble(breweryId, batchId));
        }
    }

    public static final class Close implements CostCommands.Close {

        private final BatchCostRepository costs;
        private final BatchCostAssembler assembler;
        private final AuditTrail audit;

        public Close(BatchCostRepository costs, BatchCostAssembler assembler, AuditTrail audit) {
            this.costs = Objects.requireNonNull(costs);
            this.assembler = Objects.requireNonNull(assembler);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public BatchCost handle(UUID actorId, UUID breweryId, UUID batchId, String note) {
            if (costs.findByBatch(breweryId, batchId).isPresent()) {
                // Refazer o cálculo é reabrir, e reabrir não existe: custo fechado é evidência, e
                // evidência que se sobrescreve não é evidência.
                throw new IllegalStateException("o custo deste lote já foi fechado");
            }
            var closed = assembler.assemble(breweryId, batchId).close(actorId, note, Instant.now());
            costs.insert(closed);

            audit.record(AuditEvent.success(breweryId, actorId, "costing.batch.close",
                    "production.batch", batchId.toString(),
                    Map.of("code", closed.batchCode(),
                            "total", closed.total().toPlainString(),
                            "volumeLiters", closed.volumeLiters().toPlainString(),
                            "costPerLiter", closed.costPerLiter().toPlainString(),
                            "lines", String.valueOf(closed.lines().size()),
                            "gaps", String.valueOf(closed.gaps().size()))));
            return closed;
        }
    }
}
