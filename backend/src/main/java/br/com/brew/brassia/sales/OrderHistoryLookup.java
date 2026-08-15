package br.com.brew.brassia.sales;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * O histórico de demanda por produto (FCST-001).
 *
 * <p>Consulta publicada, e não porta invertida: quem tem o dado do pedido é vendas, então quem quer
 * prever pergunta aqui. É a regra do ADR-0016 na direção padrão.
 *
 * <p><strong>Demanda é o que foi pedido e não foi cancelado.</strong> Pedido cancelado sai da conta, e a
 * escolha merece registro: ele foi uma intenção que não virou venda, e contá-lo faria a previsão
 * enxergar uma demanda que a cervejaria nunca atendeu. Contá-lo teria defesa — cancelamento por falta de
 * estoque É demanda reprimida —, mas a plataforma não distingue quem cancelou nem por quê, e tratar os
 * dois casos como um só inventaria informação.
 */
public interface OrderHistoryLookup {

    /**
     * Unidades vendidas por mês, do mais antigo para o mais recente, sem buracos.
     *
     * <p><strong>Mês sem venda entra como zero, e não é omitido.</strong> Omitir encurtaria a série e
     * faria a média subir — a previsão passaria a descrever só os meses bons, que é o erro mais fácil de
     * cometer aqui e o mais difícil de perceber depois.
     */
    List<MonthlyDemand> monthlyDemand(UUID breweryId, UUID productId, YearMonth from, YearMonth to);

    record MonthlyDemand(YearMonth month, BigDecimal units) {}
}
