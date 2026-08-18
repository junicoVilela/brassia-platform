package br.com.brew.brassia.forecast.adapter.inbound.web;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import java.util.Map;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import br.com.brew.brassia.forecast.domain.ProductionCapacity;
import br.com.brew.brassia.forecast.application.service.CapacityService;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.audit.AuditEvent;
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
    private final CapacityService capacity;
    private final AuditTrail audit;

    DemandForecastController(DemandForecastService forecasts, CapacityService capacity,
            AuditTrail audit) {
        this.forecasts = Objects.requireNonNull(forecasts);
        this.capacity = Objects.requireNonNull(capacity);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * O ciclo de ocupação de cada fermentador, declarado pela casa (DUV-FCST-001).
     *
     * <p><strong>O sistema não infere o ciclo.</strong> Quantos dias uma cerveja ocupa o tanque depende do
     * estilo, da temperatura e do que a casa aceita; inferir de lotes passados daria um número que parece
     * cálculo e é média de coisas diferentes.
     */
    @PutMapping("/tank-cycles/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void declareCycle(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID equipmentId, @Valid @RequestBody CycleRequest request) {
        principal.requirePermission("forecast.capacity.manage");
        var brewery = principal.requireBrewery();
        capacity.declare(brewery, equipmentId, request.cycleDays(), request.note(),
                principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "forecast.tank-cycle.declare",
                "equipment", equipmentId.toString(),
                Map.of("cycleDays", String.valueOf(request.cycleDays()))));
    }

    /** Tirar o tanque da conta: ele saiu de operação, virou maturador. */
    @DeleteMapping("/tank-cycles/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeCycle(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID equipmentId) {
        principal.requirePermission("forecast.capacity.manage");
        capacity.remove(principal.requireBrewery(), equipmentId);
    }

    /**
     * A capacidade do próximo mês, e se a demanda prevista cabe.
     *
     * <p><strong>Sem tanque declarado a resposta é "não sei", e não zero.</strong> Zero diria que a
     * cervejaria não consegue produzir nada, e alguém planejaria em cima disso — mesma escolha que a
     * previsão faz com histórico curto.
     *
     * <p><strong>É um teto otimista.</strong> Ele não sabe de turno, calendário, limpeza entre lotes nem
     * de gargalo fora do fermentador — maturação a frio, linha de envase, mão de obra. Se a demanda não
     * cabe aqui, ela certamente não cabe na fábrica; o contrário não vale.
     */
    @GetMapping("/products/{productId}/capacity")
    CapacityView capacity(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID productId) {
        principal.requirePermission("forecast.demand.read");
        var brewery = principal.requireBrewery();
        var previsao = forecasts.nextMonth(brewery, productId);
        var teto = capacity.of(brewery, java.time.YearMonth.now().plusMonths(1));
        var demanda = previsao.expectedUnits() == null ? BigDecimal.ZERO : previsao.expectedUnits();
        return new CapacityView(teto.known(), teto.known() ? teto.litersInPeriod() : null, demanda,
                teto.fits(demanda).orElse(null), teto.headroomLiters(demanda).orElse(null),
                teto.utilizationPercent(demanda).orElse(null),
                teto.tanks().stream().map(ProductionCapacity.Tank::equipmentCode).toList());
    }

    record CycleRequest(@NotNull @Min(1) Integer cycleDays, @Size(max = 300) String note) {}

    /**
     * @param known falso quando a casa não declarou tanque — e aí todos os demais campos são nulos, em
     *              vez de zero: zero é um número, e um número aqui seria uma afirmação
     */
    record CapacityView(boolean known, BigDecimal capacityLiters, BigDecimal demandLiters,
            Boolean fits, BigDecimal headroomLiters, BigDecimal utilizationPercent,
            java.util.List<String> tanks) {}

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
