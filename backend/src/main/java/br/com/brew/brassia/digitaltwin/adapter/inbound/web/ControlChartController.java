package br.com.brew.brassia.digitaltwin.adapter.inbound.web;

import br.com.brew.brassia.digitaltwin.application.port.inbound.ControlChartQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carta de controle vista de fora (SPC-001).
 *
 * <p><strong>É `POST` e não `GET`, e não é por alterar nada.</strong> A amostra é uma lista de lotes que
 * pode ter dezenas de itens, e uma lista dessas numa query string estoura limite de URL em produção — o
 * tipo de defeito que só aparece quando alguém analisa um ano de histórico. O corpo é o lugar de uma lista.
 *
 * <p>Só exige {@code digitaltwin.profile.read}: analisar uma série não grava nada e não escolhe número
 * nenhum — a carta é uma leitura das medições que já existem.
 */
@RestController
@RequestMapping("/api/v1/digital-twin/control-charts")
final class ControlChartController {

    private final ControlChartQueries charts;

    ControlChartController(ControlChartQueries charts) {
        this.charts = charts;
    }

    @PostMapping
    ControlChartQueries.Chart analyze(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody AnalyzeRequest request) {
        principal.requirePermission("digitaltwin.profile.read");
        return charts.analyze(new ControlChartQueries.Request(principal.requireBrewery(),
                request.recipeId(), request.kind(), request.batchIds()));
    }

    record AnalyzeRequest(
            @NotNull UUID recipeId,
            @NotBlank String kind,
            @NotEmpty List<UUID> batchIds) {
    }
}
