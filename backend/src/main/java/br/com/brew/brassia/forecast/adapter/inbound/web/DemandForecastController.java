package br.com.brew.brassia.forecast.adapter.inbound.web;

import br.com.brew.brassia.forecast.application.service.DemandForecastService;
import br.com.brew.brassia.forecast.domain.DemandForecast;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Previsão de demanda por produto (FCST-001).
 *
 * <p><strong>Só leitura, e de propósito.</strong> O critério transversal da sprint é explícito: previsão
 * não cria ordem de produção nem compra sem confirmação. Não existe endpoint aqui que produza efeito —
 * quem decide brassar usa o planejamento, olhando este número como informação e não como instrução.
 */
@RestController
@RequestMapping("/api/v1/forecast")
final class DemandForecastController {

    private final DemandForecastService forecasts;

    DemandForecastController(DemandForecastService forecasts) {
        this.forecasts = Objects.requireNonNull(forecasts);
    }

    @GetMapping("/products/{productId}/demand")
    ForecastView demand(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID productId) {
        principal.requirePermission("forecast.demand.read");
        return ForecastView.from(forecasts.nextMonth(principal.requireBrewery(), productId));
    }

    /**
     * As quatro coisas que o aceite pede viajam juntas: dados, versão, erro e confiança.
     *
     * <p>Quando não há previsão, os números vêm nulos e a confiança diz {@code INSUFFICIENT} — a tela
     * mostra a ausência, e não um zero que pareceria demanda nenhuma.
     */
    record ForecastView(UUID productId, String forMonth, BigDecimal expectedUnits, BigDecimal lowerBound,
            BigDecimal upperBound, int sampleMonths, String method,
            BigDecimal meanAbsolutePercentageError, String confidence, boolean hasNumbers) {

        static ForecastView from(DemandForecast f) {
            return new ForecastView(f.productId(), f.forMonth().toString(), f.expectedUnits(),
                    f.lowerBound(), f.upperBound(), f.sampleMonths(), f.method().label(),
                    f.meanAbsolutePercentageError(), f.confidence().name(), f.hasNumbers());
        }
    }
}
