package br.com.brew.brassia.forecast.application.service;

import br.com.brew.brassia.forecast.domain.DemandForecast;
import br.com.brew.brassia.forecast.domain.ForecastMethod;
import br.com.brew.brassia.sales.OrderHistoryLookup;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Monta a previsão a partir do histórico de pedidos (FCST-001).
 *
 * <p><strong>Nada é persistido.</strong> A previsão é derivada do histórico no momento da pergunta, como
 * o custo aberto do lote: guardá-la criaria uma segunda verdade que envelhece a cada pedido novo, e
 * alguém acabaria decidindo em cima de um número calculado no mês passado sem saber disso.
 *
 * <p>O relógio é injetado para o teste poder perguntar por um mês específico sem depender de quando ele
 * roda — a lição das âncoras de data que já custou um build a este projeto.
 */
public class DemandForecastService {

    /** Doze meses de janela: é o que permite a sazonalidade aparecer, quando ela existe no dado. */
    private static final int WINDOW_MONTHS = 12;

    private final OrderHistoryLookup history;
    private final Clock clock;

    public DemandForecastService(OrderHistoryLookup history, Clock clock) {
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    /** A previsão do próximo mês, com a janela dos doze meses fechados anteriores. */
    public DemandForecast nextMonth(UUID breweryId, UUID productId) {
        var mesAtual = YearMonth.now(clock);
        // O mês corrente fica de FORA da janela: ele está incompleto, e incluí-lo faria a previsão
        // baixar todo dia 1º e subir até o dia 31, sem nada ter mudado na demanda.
        var ate = mesAtual.minusMonths(1);
        var de = ate.minusMonths(WINDOW_MONTHS - 1L);
        var serie = history.monthlyDemand(breweryId, productId, de, ate);
        return forecast(productId, mesAtual.plusMonths(1), serie);
    }

    private DemandForecast forecast(UUID productId, YearMonth alvo,
            List<OrderHistoryLookup.MonthlyDemand> serie) {
        // Meses iniciais sem venda nenhuma são cortados: eles são "o produto ainda não existia", e não
        // "ninguém quis". Contá-los como zero faria um lançamento recente parecer um fracasso.
        var primeiroComVenda = 0;
        while (primeiroComVenda < serie.size() && serie.get(primeiroComVenda).units().signum() == 0) {
            primeiroComVenda++;
        }
        var util = serie.subList(primeiroComVenda, serie.size()).stream()
                .map(OrderHistoryLookup.MonthlyDemand::units)
                .toList();
        return DemandForecast.from(productId, alvo, util, ForecastMethod.MOVING_AVERAGE_V1);
    }
}
