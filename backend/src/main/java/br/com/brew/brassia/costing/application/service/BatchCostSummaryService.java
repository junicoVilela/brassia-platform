package br.com.brew.brassia.costing.application.service;

import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.costing.application.port.inbound.CostQueries;
import br.com.brew.brassia.costing.domain.UnknownBatchCostException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O resumo do custo para fora do módulo (RPT-001).
 *
 * <p>Passa pela mesma consulta que a tela usa, e não por um SQL próprio: fosse por SQL, o
 * relatório responderia do que está gravado e a tela do que está derivado, e um lote aberto teria
 * dois custos diferentes na mesma casa.
 */
public final class BatchCostSummaryService implements BatchCostLookup {

    private final CostQueries queries;

    public BatchCostSummaryService(CostQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }

    @Override
    public Optional<CostSummary> ofBatch(UUID breweryId, UUID batchId) {
        try {
            var cost = queries.ofBatch(breweryId, batchId);
            return Optional.of(new CostSummary(cost.total(), cost.costPerLiter(), cost.volumeLiters(),
                    cost.closed(), cost.incomplete(),
                    cost.gaps().stream().map(gap -> gap.category() + ": " + gap.reason()).toList()));
        } catch (UnknownBatchCostException ex) {
            // Quem pergunta pelo custo de um lote que não existe recebe vazio; recusar é papel de
            // quem foi perguntado sobre o lote, não de quem só soma o custo dele.
            return Optional.empty();
        }
    }
}
