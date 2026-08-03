package br.com.brew.brassia.metrology.adapter.inbound.web;

import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyDtos;
import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyViews;
import br.com.brew.brassia.metrology.application.port.inbound.InstrumentCommands;
import br.com.brew.brassia.metrology.application.port.inbound.MetrologyQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correção metrológica de leitura (MTR-002).
 *
 * <p>O nome distingue de propósito da {@code production.CorrectionController} (CAL-002), que
 * registra correção <em>de processo</em> no dia de brassa. Aqui é correção <em>metrológica</em>:
 * o que o instrumento leu versus o que ele deveria ter lido.
 *
 * <p>Não existe endpoint para alterar uma correção: o bruto é imutável e corrigir de novo cria
 * outro registro. Sobrescrever apagaria o rastro de como um número foi obtido, que é justamente o
 * que a história existe para preservar.
 */
@RestController
@RequestMapping("/api/v1/metrology/corrections")
final class ReadingCorrectionController {

    private final InstrumentCommands.CorrectReading correct;
    private final MetrologyQueries queries;

    ReadingCorrectionController(InstrumentCommands.CorrectReading correct, MetrologyQueries queries) {
        this.correct = correct;
        this.queries = queries;
    }

    @GetMapping
    List<MetrologyViews.CorrectionView> list(@RequestParam UUID instrumentId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        return queries.corrections(principal.requireBrewery(), instrumentId).stream()
                .map(MetrologyViews.CorrectionView::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<MetrologyViews.CorrectionView> correct(
            @Valid @RequestBody MetrologyDtos.CorrectReading body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.manage");
        var brewery = principal.requireBrewery();
        var id = correct.handle(new InstrumentCommands.CorrectReading.Command(principal.userId(), brewery,
                body.instrumentId(), body.sourceReadingId(), body.rawValue(), body.unit(), body.sampleTempC(),
                body.calibrationTempC(), body.applyCurve()));
        var view = queries.corrections(brewery, body.instrumentId()).stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .map(MetrologyViews.CorrectionView::from)
                .orElseThrow(() -> new IllegalStateException("correção não encontrada após o comando"));
        return ResponseEntity.created(URI.create("/api/v1/metrology/corrections/" + id)).body(view);
    }
}
