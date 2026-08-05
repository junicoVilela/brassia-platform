package br.com.brew.brassia.costing.application.service;

import br.com.brew.brassia.costing.CostContributor;
import br.com.brew.brassia.costing.CostContributor.CostCategory;
import br.com.brew.brassia.costing.CostContributor.CostGap;
import br.com.brew.brassia.costing.CostContributor.CostLine;
import br.com.brew.brassia.costing.CostContributor.CostScope;
import br.com.brew.brassia.costing.domain.BatchCost;
import br.com.brew.brassia.costing.domain.UnknownBatchCostException;
import br.com.brew.brassia.production.BatchLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Monta o custo aberto de um lote: pergunta a cada contribuinte e junta (CST-001).
 *
 * <p>Não sabe quais módulos existem: recebe a lista de contribuintes e pergunta a todos. Quando a
 * mão de obra e as utilidades ganharem fonte, elas entram implementando a porta, e nem uma linha
 * daqui muda — a lacuna simplesmente para de ser declarada.
 *
 * <p>O recorte que ele passa é mínimo — lote e ordem. Quem sabe o que mais pertence ao lote é o
 * módulo dono do dado, que resolve isso pela consulta publicada de quem o tem; o custo não precisa
 * conhecer o mundo inteiro para somá-lo.
 */
public final class BatchCostAssembler {

    private final BatchLookup batches;
    private final List<CostContributor> contributors;

    public BatchCostAssembler(BatchLookup batches, List<CostContributor> contributors) {
        this.batches = Objects.requireNonNull(batches);
        this.contributors = List.copyOf(Objects.requireNonNull(contributors));
    }

    public BatchCost assemble(UUID breweryId, UUID batchId) {
        var batch = batches.find(breweryId, batchId)
                .orElseThrow(() -> new UnknownBatchCostException(batchId));
        var scope = new CostScope(batchId, batch.orderId());

        var lines = new ArrayList<CostLine>();
        var gaps = new ArrayList<CostGap>();
        for (CostContributor contributor : contributors) {
            lines.addAll(contributor.linesFor(breweryId, scope));
            gaps.addAll(contributor.gapsFor(breweryId, scope));
        }
        gaps.addAll(structuralGaps(lines));

        return BatchCost.open(breweryId, batchId, batch.code(), batch.packageableVolumeLiters(), lines,
                gaps);
    }

    /**
     * As lacunas que nenhum módulo declara porque nenhum módulo existe para declará-las.
     *
     * <p>Mão de obra e utilidade não têm dono: não há hora trabalhada registrada em lugar nenhum, e
     * a água e a energia que a sanitização mede são por equipamento, não por lote. Declarar aqui é
     * o que evita que o total pareça completo — somar zero seria mentir por omissão.
     */
    private static List<CostGap> structuralGaps(List<CostLine> lines) {
        var gaps = new ArrayList<CostGap>();
        if (lines.stream().noneMatch(line -> line.category() == CostCategory.LABOR)) {
            gaps.add(new CostGap(CostCategory.LABOR,
                    "não há hora trabalhada registrada na plataforma: a mão de obra não entra no total "
                            + "(CST-001-A)"));
        }
        if (lines.stream().noneMatch(line -> line.category() == CostCategory.UTILITY)) {
            gaps.add(new CostGap(CostCategory.UTILITY,
                    "água e energia são medidas por ciclo de limpeza, por equipamento, e o CO₂ não tem "
                            + "preço nem vínculo com o lote: nenhuma utilidade entra no total (CST-001-B)"));
        }
        return gaps;
    }

}
