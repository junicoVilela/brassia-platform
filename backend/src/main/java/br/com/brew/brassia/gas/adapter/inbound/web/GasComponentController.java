package br.com.brew.brassia.gas.adapter.inbound.web;

import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasDtos;
import br.com.brew.brassia.gas.adapter.inbound.web.dto.GasViews;
import br.com.brew.brassia.gas.application.port.inbound.ComponentCommands;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rede de gás (GAS-001): reguladores e manifolds. */
@RestController
@RequestMapping("/api/v1/gas/components")
final class GasComponentController {

    private final ComponentCommands.Register register;
    private final ComponentCommands.Update update;
    private final ComponentCommands.SetActive setActive;
    private final GasQueries queries;

    GasComponentController(ComponentCommands.Register register, ComponentCommands.Update update,
            ComponentCommands.SetActive setActive, GasQueries queries) {
        this.register = register;
        this.update = update;
        this.setActive = setActive;
        this.queries = queries;
    }

    @GetMapping
    List<GasViews.ComponentView> list(@RequestParam(required = false) String kind,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.read");
        return queries.components(principal.requireBrewery(), kind).stream()
                .map(GasViews.ComponentView::from)
                .toList();
    }

    @PostMapping
    ResponseEntity<Registered> register(@Valid @RequestBody GasDtos.RegisterComponentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        var id = register.handle(new ComponentCommands.Register.Command(
                principal.userId(), principal.requireBrewery(), request.kind(), request.code(), request.name(),
                request.maxPressureBar(), request.setPressureBar()));
        return ResponseEntity.created(URI.create("/api/v1/gas/components/" + id)).body(new Registered(id));
    }

    record Registered(UUID id) {}

    @PutMapping("/{id}")
    void update(@PathVariable UUID id, @Valid @RequestBody GasDtos.UpdateComponentRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        update.handle(new ComponentCommands.Update.Command(
                principal.userId(), principal.requireBrewery(), id, request.name(), request.maxPressureBar(),
                request.setPressureBar()));
    }

    @PostMapping("/{id}/active")
    void setActive(@PathVariable UUID id, @Valid @RequestBody GasDtos.SetActiveRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("gas.manage");
        setActive.handle(new ComponentCommands.SetActive.Command(
                principal.userId(), principal.requireBrewery(), id, request.active()));
    }
}
