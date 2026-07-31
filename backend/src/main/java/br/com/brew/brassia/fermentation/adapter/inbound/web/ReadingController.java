package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.ReadingView;
import br.com.brew.brassia.fermentation.adapter.inbound.web.dto.RecordReadingRequest;
import br.com.brew.brassia.fermentation.application.port.inbound.ListReadingsUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.RecordReadingUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leituras e curvas de fermentação (FER-002); ingestão idempotente pela chave natural. */
@RestController
@RequestMapping("/api/v1/fermentation/readings")
final class ReadingController {

    private final RecordReadingUseCase record;
    private final ListReadingsUseCase list;

    ReadingController(RecordReadingUseCase record, ListReadingsUseCase list) {
        this.record = record;
        this.list = list;
    }

    @GetMapping
    List<ReadingView> list(@RequestParam UUID batchId, @RequestParam(required = false) String kind,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.reading.read");
        return list.handle(principal.requireBrewery(), batchId, kind).stream().map(ReadingView::from).toList();
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> record(@Valid @RequestBody RecordReadingRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.reading.record");
        var result = record.handle(new RecordReadingUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.batchId(), request.kind(), request.source(),
                request.value(), request.unit(), request.measuredAt()));
        var body = Map.<String, Object>of("id", result.id(), "valid", result.valid(),
                "invalidReason", result.invalidReason() == null ? "" : result.invalidReason());
        // Idempotente: 201 quando cria, 200 quando o reenvio casa a chave natural.
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/fermentation/readings/" + result.id())).body(body)
                : ResponseEntity.ok(body);
    }
}
