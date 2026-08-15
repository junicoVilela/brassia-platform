package br.com.brew.brassia.costing.adapter.inbound.gateway;

import br.com.brew.brassia.costing.CostContributor;
import br.com.brew.brassia.costing.application.port.outbound.LaborRateRepository;
import br.com.brew.brassia.production.LaborLookup;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A mão de obra do lote (CST-001-A).
 *
 * <p><strong>Este contribuinte mora no custeio, e não na produção</strong>, porque a parcela é
 * {@code hora × taxa} e a taxa é dinheiro — decisão de gestão, não de quem aponta. A produção publica a
 * hora; o custeio sabe quanto ela vale. Assim a taxa muda sem reescrever apontamento nenhum, e quem
 * registra seis horas de brassa não precisa conhecer moeda para fazê-lo.
 *
 * <p><strong>Sem taxa cadastrada, a parcela vira lacuna, não zero.</strong> É a mesma regra que manteve a
 * mão de obra declarada como ausente até aqui: um custo que soma zero mente por omissão; um que diz "sem
 * taxa de hora cadastrada" é utilizável.
 */
@Component
class LaborCostContributor implements CostContributor {

    private final LaborLookup labor;
    private final LaborRateRepository rates;

    LaborCostContributor(LaborLookup labor, LaborRateRepository rates) {
        this.labor = Objects.requireNonNull(labor, "labor");
        this.rates = Objects.requireNonNull(rates, "rates");
    }

    @Override
    public List<CostLine> linesFor(UUID breweryId, CostScope scope) {
        var rate = rates.find(breweryId).orElse(null);
        if (rate == null) {
            return List.of();
        }
        // Uma linha por atividade, e não um total: "seis horas de trabalho" não se discute; "quatro horas
        // de brassa e duas de limpeza" se discute, que é o que um custo serve para permitir.
        return labor.ofBatch(breweryId, scope.batchId()).stream()
                .map(time -> new CostLine(CostCategory.LABOR, time.activity(),
                        "apontamento de hora no lote",
                        time.manHours(), "h", rate,
                        time.manHours().multiply(rate).setScale(4, RoundingMode.HALF_UP)))
                .toList();
    }

    @Override
    public List<CostGap> gapsFor(UUID breweryId, CostScope scope) {
        if (rates.find(breweryId).isEmpty()) {
            return List.of(new CostGap(CostCategory.LABOR,
                    "A hora de trabalho não tem custo cadastrado: defina a taxa em Custos para que a mão "
                            + "de obra entre no total."));
        }
        if (labor.ofBatch(breweryId, scope.batchId()).isEmpty()) {
            // A taxa existe e ninguém apontou: dizer isso é diferente de somar zero, porque um lote sem
            // apontamento não é um lote que ninguém trabalhou.
            return List.of(new CostGap(CostCategory.LABOR,
                    "Nenhuma hora foi apontada neste lote: o total não inclui mão de obra."));
        }
        return List.of();
    }
}
