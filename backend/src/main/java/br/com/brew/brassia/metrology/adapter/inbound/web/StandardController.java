package br.com.brew.brassia.metrology.adapter.inbound.web;

import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyDtos;
import br.com.brew.brassia.metrology.adapter.inbound.web.dto.MetrologyViews;
import br.com.brew.brassia.metrology.application.port.inbound.MetrologyQueries;
import br.com.brew.brassia.metrology.application.port.inbound.StandardCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Padrões de referência (MTR-001). Cadastrar padrão é alçada própria: é ele que sustenta a
 * rastreabilidade de toda calibração feita contra ele.
 */
@RestController
@RequestMapping("/api/v1/metrology/standards")
final class StandardController {

    private final StandardCommands.Register register;
    private final StandardCommands.Renew renew;
    private final MetrologyQueries queries;

    StandardController(StandardCommands.Register register, StandardCommands.Renew renew,
            MetrologyQueries queries) {
        this.register = register;
        this.renew = renew;
        this.queries = queries;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @GetMapping
    List<MetrologyViews.StandardView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.instrument.read");
        var on = today();
        return queries.standards(principal.requireBrewery()).stream()
                .map(s -> MetrologyViews.StandardView.from(s, on))
                .toList();
    }

    @PostMapping
    ResponseEntity<MetrologyViews.StandardView> register(@Valid @RequestBody MetrologyDtos.RegisterStandard body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.standard.manage");
        var brewery = principal.requireBrewery();
        var id = register.handle(new StandardCommands.Register.Command(principal.userId(), brewery,
                body.code(), body.description(), body.certificateNumber(), body.issuer(), body.traceability(),
                body.validUntil()));
        return ResponseEntity.created(URI.create("/api/v1/metrology/standards/" + id)).body(view(brewery, id));
    }

    @PutMapping("/{id}")
    MetrologyViews.StandardView renew(@PathVariable UUID id,
            @Valid @RequestBody MetrologyDtos.RenewStandard body,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("metrology.standard.manage");
        var brewery = principal.requireBrewery();
        renew.handle(new StandardCommands.Renew.Command(principal.userId(), brewery, id,
                body.certificateNumber(), body.issuer(), body.validUntil(), body.issuedOn()));
        return view(brewery, id);
    }

    private MetrologyViews.StandardView view(UUID brewery, UUID id) {
        return queries.standards(brewery).stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .map(s -> MetrologyViews.StandardView.from(s, today()))
                .orElseThrow(() -> new IllegalStateException("padrão não encontrado após o comando"));
    }
}
