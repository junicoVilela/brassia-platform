package br.com.brew.brassia.brewery.adapter.inbound.web;

import br.com.brew.brassia.brewery.adapter.inbound.web.dto.PreferencesResponse;
import br.com.brew.brassia.brewery.adapter.inbound.web.dto.UpdatePreferencesRequest;
import br.com.brew.brassia.brewery.application.port.inbound.GetOperationalPreferencesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.GetPreferencesRevisionUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.UpdateOperationalPreferencesUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/breweries/active/preferences")
final class BreweryPreferencesController {
    private final GetOperationalPreferencesUseCase getPreferences;
    private final UpdateOperationalPreferencesUseCase updatePreferences;
    private final GetPreferencesRevisionUseCase getRevision;

    BreweryPreferencesController(
            GetOperationalPreferencesUseCase getPreferences,
            UpdateOperationalPreferencesUseCase updatePreferences,
            GetPreferencesRevisionUseCase getRevision) {
        this.getPreferences = getPreferences;
        this.updatePreferences = updatePreferences;
        this.getRevision = getRevision;
    }

    @GetMapping
    PreferencesResponse get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.preferences.read");
        var result = getPreferences.handle(new GetOperationalPreferencesUseCase.Query(principal.requireBrewery()));
        return PreferencesResponse.from(result);
    }

    @PutMapping
    PreferencesResponse update(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.preferences.manage");
        var result = updatePreferences.handle(new UpdateOperationalPreferencesUseCase.Command(
                principal.userId(),
                principal.requireBrewery(),
                request.volumeUnit(),
                request.massUnit(),
                request.temperatureUnit(),
                request.currencyCode(),
                request.maxBatchVolume(),
                request.allowNegativeStock(),
                request.stockPolicy(),
                request.version()));
        return PreferencesResponse.from(result);
    }

    @GetMapping("/revisions/{version}")
    PreferencesResponse revision(
            @PathVariable long version, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("brewery.preferences.read");
        var snap = getRevision.handle(new GetPreferencesRevisionUseCase.Query(principal.requireBrewery(), version));
        return PreferencesResponse.from(snap);
    }
}
