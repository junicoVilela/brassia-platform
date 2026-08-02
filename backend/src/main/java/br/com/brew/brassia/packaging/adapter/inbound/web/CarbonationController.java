package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.CarbonationDtos;
import br.com.brew.brassia.packaging.application.port.inbound.CarbonationCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Carbonatação do plano de envase (PKG-002): prévia, decisão confirmada e consulta. */
@RestController
@RequestMapping("/api/v1/packaging/plans/{id}/carbonation")
final class CarbonationController {

    private final CarbonationCommands.Preview preview;
    private final CarbonationCommands.Record record;
    private final CarbonationCommands.Get get;

    CarbonationController(CarbonationCommands.Preview preview, CarbonationCommands.Record record,
            CarbonationCommands.Get get) {
        this.preview = preview;
        this.record = record;
        this.get = get;
    }

    /** Calcula e explica sem gravar: é recomendação, não decisão. */
    @GetMapping("/preview")
    CarbonationDtos.RecommendationView preview(@PathVariable UUID id,
            @RequestParam String method,
            @RequestParam BigDecimal targetVolumes,
            @RequestParam BigDecimal referenceTempC,
            @RequestParam(required = false) String primingSugar,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return CarbonationDtos.RecommendationView.from(preview.handle(new CarbonationCommands.Preview.Query(
                principal.requireBrewery(), id, method, targetVolumes, referenceTempC, primingSugar)));
    }

    @GetMapping
    CarbonationDtos.CarbonationView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return get.handle(principal.requireBrewery(), id)
                .map(CarbonationDtos.CarbonationView::from)
                .orElseThrow(() -> new IllegalArgumentException("plano sem carbonatação decidida"));
    }

    /** Grava a decisão; {@code confirmed} falso é recusado. Recalcular substitui a decisão inteira. */
    @PutMapping
    void record(@PathVariable UUID id, @Valid @RequestBody CarbonationDtos.RecordCarbonationRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        record.handle(new CarbonationCommands.Record.Command(
                principal.userId(), principal.requireBrewery(), id, request.method(), request.targetVolumes(),
                request.referenceTempC(), request.primingSugar(), request.confirmed()));
    }
}
