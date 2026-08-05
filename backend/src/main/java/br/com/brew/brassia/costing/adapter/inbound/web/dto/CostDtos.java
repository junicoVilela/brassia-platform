package br.com.brew.brassia.costing.adapter.inbound.web.dto;

import br.com.brew.brassia.costing.CostContributor.CostGap;
import br.com.brew.brassia.costing.CostContributor.CostLine;
import br.com.brew.brassia.costing.domain.BatchCost;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Contratos do custo do lote (CST-001). */
public final class CostDtos {

    private CostDtos() {
    }

    public record CloseRequest(@Size(max = 500) String note) {}

    public record LineView(String category, String description, String source, BigDecimal quantity,
            String unit, BigDecimal unitCost, BigDecimal total) {

        static LineView from(CostLine line) {
            return new LineView(line.category().name(), line.description(), line.source(),
                    line.quantity(), line.unit(), line.unitCost(), line.total());
        }
    }

    public record GapView(String category, String reason) {

        static GapView from(CostGap gap) {
            return new GapView(gap.category().name(), gap.reason());
        }
    }

    /**
     * @param closed     falso enquanto o custo é derivado: ele ainda muda se a produção mudar
     * @param incomplete verdadeiro quando alguma parcela conhecida ficou de fora; leia o total
     *                   junto das lacunas, ou ele parecerá menor do que a verdade
     */
    public record BatchCostView(UUID batchId, String batchCode, boolean closed, boolean incomplete,
            BigDecimal volumeLiters, BigDecimal total, BigDecimal costPerLiter,
            Map<String, BigDecimal> totalByCategory, List<LineView> lines, List<GapView> gaps,
            Instant closedAt, String note) {

        public static BatchCostView from(BatchCost cost) {
            return new BatchCostView(cost.batchId(), cost.batchCode(), cost.closed(), cost.incomplete(),
                    cost.volumeLiters(), cost.total(), cost.costPerLiter(),
                    cost.totalByCategory().entrySet().stream()
                            .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
                    cost.lines().stream().map(LineView::from).toList(),
                    cost.gaps().stream().map(GapView::from).toList(),
                    cost.closedAt(), cost.note());
        }

        public static List<BatchCostView> from(List<BatchCost> costs) {
            return costs.stream().map(BatchCostView::from).toList();
        }
    }
}
