package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.FreshnessDtos;
import br.com.brew.brassia.packaging.application.port.inbound.FreshnessCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Oxigênio e vida útil do envase (FSL-001): evidência, recomendação e override auditado. */
@RestController
@RequestMapping("/api/v1/packaging")
final class FreshnessController {

    private final FreshnessCommands.Record record;
    private final FreshnessCommands.OverrideShelfLife override;
    private final FreshnessCommands.Get get;
    private final FreshnessCommands.Policy policy;

    FreshnessController(FreshnessCommands.Record record, FreshnessCommands.OverrideShelfLife override,
            FreshnessCommands.Get get, FreshnessCommands.Policy policy) {
        this.record = record;
        this.override = override;
        this.get = get;
        this.policy = policy;
    }

    @GetMapping("/plans/{id}/freshness")
    FreshnessDtos.FreshnessView get(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return get.handle(principal.requireBrewery(), id)
                .map(FreshnessDtos.FreshnessView::from)
                .orElseThrow(() -> new IllegalArgumentException("plano sem controle de frescor registrado"));
    }

    /** Grava DO/TPO, purga e vedação, e devolve a validade recomendada com a evidência. */
    @PutMapping("/plans/{id}/freshness")
    FreshnessDtos.RecordedView record(@PathVariable UUID id,
            @Valid @RequestBody FreshnessDtos.RecordFreshnessRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        var result = record.handle(new FreshnessCommands.Record.Command(
                principal.userId(), principal.requireBrewery(), id, request.dissolvedOxygenPpb(),
                request.totalPackageOxygenPpb(), request.purgeMethod(), request.purgeVerified(),
                request.sealCheckMethod(), request.sealCheckPassed()));
        return FreshnessDtos.RecordedView.from(result.record(), result.recommendation());
    }

    /** Sobrepõe a validade recomendada; motivo obrigatório e override auditado. */
    @PostMapping("/plans/{id}/freshness/override")
    void override(@PathVariable UUID id,
            @Valid @RequestBody FreshnessDtos.OverrideShelfLifeRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        override.handle(new FreshnessCommands.OverrideShelfLife.Command(
                principal.userId(), principal.requireBrewery(), id, request.shelfLifeDays(), request.reason()));
    }

    @GetMapping("/shelf-life-policy")
    FreshnessDtos.ShelfLifePolicyView policy(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return policy.get(principal.requireBrewery())
                .map(FreshnessDtos.ShelfLifePolicyView::from)
                .orElseThrow(() -> new IllegalArgumentException("cervejaria sem política de vida útil"));
    }

    @PutMapping("/shelf-life-policy")
    void savePolicy(@Valid @RequestBody FreshnessDtos.ShelfLifePolicyRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.policy.manage");
        policy.save(principal.userId(), principal.requireBrewery(), request.toPolicy());
    }
}
