package br.com.brew.brassia.costing;

import br.com.brew.brassia.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O custo de um lote, resumido para quem só quer o número (RPT-001).
 *
 * <p>Publica o suficiente para um relatório e nada além: total, por litro, e se o número ainda
 * muda. O detalhe parcela a parcela continua sendo assunto da consulta do custo — expor o
 * {@code BatchCost} inteiro faria de qualquer mudança nele uma mudança no contrato de todo mundo.
 */
public interface BatchCostLookup {

    Optional<CostSummary> ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param closed     falso enquanto o custo é derivado: ele ainda muda se a produção mudar, e um
     *                   relatório que não diga isso convida a decidir preço sobre um número vivo
     * @param incomplete alguma parcela conhecida ficou de fora; os motivos vêm em {@code gaps}
     */
    record CostSummary(Money total, Money costPerLiter, BigDecimal volumeLiters,
            boolean closed, boolean incomplete, List<String> gaps) {

        public CostSummary {
            gaps = List.copyOf(gaps);
        }
    }
}
