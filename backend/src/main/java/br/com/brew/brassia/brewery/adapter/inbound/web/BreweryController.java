package br.com.brew.brassia.brewery.adapter.inbound.web;

import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.RegisterBreweryUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    PageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.read");
        var result = listBreweries.handle(new ListBreweriesUseCase.Query(page, size));
        var content = result.content().stream()
                .map(s -> new Response(s.id(), s.code(), s.name(), s.timezone()))
                .toList();
        return new PageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping
    ResponseEntity<Response> register(
            @Valid @RequestBody Request request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.manage");
        var result = registerBrewery.handle(new RegisterBreweryUseCase.Command(
                principal.userId(), request.code(), request.name(), request.timezone()));
        return ResponseEntity.created(URI.create("/api/v1/breweries/" + result.id()))
                .body(new Response(result.id(), result.code(), result.name(), result.timezone()));
    }

    record Request(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String timezone) {}

    record Response(UUID id, String code, String name, String timezone) {}

    record PageResponse(List<Response> content, int page, int size, long totalElements, int totalPages) {}
}
