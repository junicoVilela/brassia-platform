package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityDtos;
import br.com.brew.brassia.quality.adapter.inbound.web.dto.QualityViews;
import br.com.brew.brassia.quality.application.port.inbound.MeasurementCommands;
import br.com.brew.brassia.quality.application.port.inbound.QualityQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Medições e desvios (QLT-001).
 *
 * <p>A resposta devolve o desvio junto quando ele nasce: quem registrou a medição precisa saber
 * na hora que abriu desvio e qual ação o plano manda tomar — descobrir isso numa tela de listagem
 * depois é tarde.
 */
@RestController
@RequestMapping("/api/v1/quality")
final class MeasurementController {

    private final MeasurementCommands.Record record;
    private final QualityQueries queries;

    MeasurementController(MeasurementCommands.Record record, QualityQueries queries) {
        this.record = record;
        this.queries = queries;
    }

    @PostMapping("/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    QualityViews.MeasurementOutcome measure(@Valid @RequestBody QualityDtos.RecordMeasurement body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.measurement.record");
        var brewery = principal.requireBrewery();
        var outcome = record.handle(new MeasurementCommands.Record.Command(principal.userId(), brewery,
                body.planId(), body.pointId(), body.batchId(), body.instrumentId(), body.value(),
                body.note(), body.measuredAt() == null ? Instant.now() : body.measuredAt()));

        var deviation = outcome.deviationId() == null ? null
                : queries.deviations(brewery).stream()
                        .filter(d -> d.id().equals(outcome.deviationId()))
                        .findFirst()
                        .map(QualityViews.DeviationView::from)
                        .orElse(null);
        return new QualityViews.MeasurementOutcome(outcome.measurementId(), outcome.withinSpec(),
                outcome.deviationId(), deviation);
    }

    @GetMapping("/deviations")
    List<QualityViews.DeviationView> deviations(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("quality.plan.read");
        return queries.deviations(principal.requireBrewery()).stream()
                .map(QualityViews.DeviationView::from)
                .toList();
    }
}
