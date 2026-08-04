package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasDtos;
import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasViews;
import br.com.brew.brassia.gas.application.port.inbound.CylinderCommands;
import br.com.brew.brassia.gas.application.port.inbound.GasQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
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

/** Cilindros de gás (GAS-001): cadastro, bloqueio, requalificação e recarga. */
@RestController
@RequestMapping("/api/v1/gas/cylinders")
final class GasCylinderController {

    private final CylinderCommands.Register register;
    private final CylinderCommands.SetBlock setBlock;
    private final CylinderCommands.Requalify requalify;
    private final CylinderCommands.Refill refill;
    private final GasQueries queries;

    GasCylinderController(CylinderCommands.Register register, CylinderCommands.SetBlock setBlock,
            CylinderCommands.Requalify requalify, CylinderCommands.Refill refill, GasQueries queries) {
        this.register = register;
        this.setBlock = setBlock;
        this.requalify = requalify;
        this.refill = refill;
        this.queries = queries;
    }

    @GetMapping
    List<GasViews.CylinderView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.cylinders(principal.requireBrewery()).stream().map(GasViews.CylinderView::from).toList();
    }

    @GetMapping("/{id}")
    GasViews.CylinderView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.cylinder(principal.requireBrewery(), id)
                .map(GasViews.CylinderView::from)
                .orElseThrow(() -> new IllegalArgumentException("cilindro inexistente"));
    }

    @PostMapping
    ResponseEntity<Registered> register(@Valid @RequestBody GasDtos.RegisterCylinderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var id = register.handle(new CylinderCommands.Register.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.gasType(),
                request.capacityKg(), request.tareKg(), request.contentKg(), request.requalificationDueOn(),
                request.location()));
        return ResponseEntity.created(URI.create("/api/v1/gas/cylinders/" + id)).body(new Registered(id));
    }

    record Registered(UUID id) {}

    /** Bloqueio exige motivo; desbloqueio não requalifica o cilindro vencido. */
    @PostMapping("/{id}/block")
    void block(@PathVariable UUID id, @Valid @RequestBody GasDtos.BlockCylinderRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        setBlock.handle(new CylinderCommands.SetBlock.Command(
                principal.userId(), principal.requireBrewery(), id, request.blocked(), request.reason()));
    }

    /**
     * Devolve o cilindro porque o vencimento pode ter sido <em>derivado</em> da política da
     * cervejaria (PRM-001): quem requalificou sem informar data precisa saber qual ficou valendo.
     */
    @PostMapping("/{id}/requalification")
    GasViews.CylinderView requalify(@PathVariable UUID id,
            @Valid @RequestBody GasDtos.RequalifyRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var brewery = principal.requireBrewery();
        requalify.handle(new CylinderCommands.Requalify.Command(principal.userId(), brewery, id,
                request.dueOn()));
        return queries.cylinder(brewery, id)
                .map(GasViews.CylinderView::from)
                .orElseThrow(() -> new IllegalStateException("cilindro não encontrado após o comando"));
    }

    @PostMapping("/{id}/refill")
    void refill(@PathVariable UUID id, @Valid @RequestBody GasDtos.RefillRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        refill.handle(new CylinderCommands.Refill.Command(
                principal.userId(), principal.requireBrewery(), id, request.contentKg()));
    }
}
