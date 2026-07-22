package br.com.brew.brassia.brewery.adapter.inbound.web;

import br.com.brew.brassia.brewery.adapter.inbound.web.dto.BreweryResponse;
import br.com.brew.brassia.brewery.adapter.inbound.web.dto.RegisterBreweryRequest;
import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/breweries")
final class BreweryController {
    private final RegisterBreweryUseCase registerBrewery;
    private final ListBreweriesUseCase listBreweries;

    BreweryController(RegisterBreweryUseCase registerBrewery, ListBreweriesUseCase listBreweries) {
        this.registerBrewery = registerBrewery;
        this.listBreweries = listBreweries;
    }

    @GetMapping
    PageResponse<BreweryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.read");
        var result = listBreweries.handle(new ListBreweriesUseCase.Query(page, size));
        var content = result.content().stream()
                .map(s -> new BreweryResponse(s.id(), s.code(), s.name(), s.timezone()))
                .toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<BreweryResponse> register(
            @Valid @RequestBody RegisterBreweryRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.manage");
        var result = registerBrewery.handle(new RegisterBreweryUseCase.Command(
                principal.userId(), request.code(), request.name(), request.timezone()));
        return ResponseEntity.created(URI.create("/api/v1/breweries/" + result.id()))
                .body(new BreweryResponse(result.id(), result.code(), result.name(), result.timezone()));
    }
}
