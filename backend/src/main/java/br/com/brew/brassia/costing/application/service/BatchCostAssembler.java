package br.com.brew.brassia.costing.application.service;

import br.com.brew.brassia.brewery.BreweryCurrencyLookup;
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
    private final BreweryCurrencyLookup currencies;

    public BatchCostAssembler(BatchLookup batches, List<CostContributor> contributors,
            BreweryCurrencyLookup currencies) {
        this.batches = Objects.requireNonNull(batches);
        this.contributors = List.copyOf(Objects.requireNonNull(contributors));
        this.currencies = Objects.requireNonNull(currencies);
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

        // A moeda é da CASA, e o custeio a carimba ao compor. Ela nunca falta: quem nunca configurou
        // preferências opera no padrão da plataforma, e recusar o custo por causa disso transformaria
        // "quanto custou esta brassa?" em erro de configuração.
        var currency = currencies.currencyOf(breweryId);
        return BatchCost.open(breweryId, batchId, batch.code(), batch.packageableVolumeLiters(), currency,
                lines, gaps);
    }

    /**
     * As lacunas que nenhum módulo declara porque nenhum módulo existe para declará-las.
     *
     * <p>Restou a utilidade: a água e a energia que a sanitização mede são por equipamento, não por lote.
     * Declarar aqui é o que evita que o total pareça completo — somar zero seria mentir por omissão.
     *
     * <p><strong>A mão de obra saiu daqui (CST-001-A).</strong> Ela passou a ter dono — o apontamento de
     * hora na produção e a taxa no custeio —, e quem declara a lacuna agora é o próprio contribuinte, que
     * sabe distinguir "sem taxa cadastrada" de "ninguém apontou hora neste lote". Duas ausências
     * diferentes, com ações diferentes, que uma frase genérica aqui achataria numa só.
     */
    private static List<CostGap> structuralGaps(List<CostLine> lines) {
        var gaps = new ArrayList<CostGap>();
        if (lines.stream().noneMatch(line -> line.category() == CostCategory.UTILITY)) {
            gaps.add(new CostGap(CostCategory.UTILITY,
                    "água e energia são medidas por ciclo de limpeza, por equipamento, e o CO₂ não tem "
                            + "preço nem vínculo com o lote: nenhuma utilidade entra no total (CST-001-B)"));
        }
        return gaps;
    }

}
