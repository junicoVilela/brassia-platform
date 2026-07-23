package br.com.brew.brassia.water.adapter.inbound.web;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.water.adapter.inbound.web.dto.RecordWaterReportRequest;
import br.com.brew.brassia.water.adapter.inbound.web.dto.WaterReportResponse;
import br.com.brew.brassia.water.application.port.inbound.ListWaterReportsUseCase;
import br.com.brew.brassia.water.application.port.inbound.RecordWaterReportUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/water/sources/{sourceId}/reports")
final class WaterReportController {
    private final RecordWaterReportUseCase record;
    private final ListWaterReportsUseCase list;

    WaterReportController(RecordWaterReportUseCase record, ListWaterReportsUseCase list) {
        this.record = record;
        this.list = list;
    }

    @GetMapping
    List<WaterReportResponse> list(
            @PathVariable UUID sourceId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.read");
        return list.handle(new ListWaterReportsUseCase.Query(principal.requireBrewery(), sourceId))
                .stream().map(WaterReportResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<WaterReportResponse> record(
            @PathVariable UUID sourceId,
            @Valid @RequestBody RecordWaterReportRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("water.manage");
        var result = record.handle(new RecordWaterReportUseCase.Command(
                principal.userId(), principal.requireBrewery(), sourceId, request.collectedOn(), request.method(),
                request.calcium(), request.magnesium(), request.sodium(), request.sulfate(), request.chloride(),
                request.bicarbonate(), request.notes()));
        return ResponseEntity
                .created(URI.create("/api/v1/water/sources/" + sourceId + "/reports/" + result.id()))
                .body(WaterReportResponse.from(result));
    }
}
