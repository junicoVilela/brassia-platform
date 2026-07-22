package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.brewery.application.port.inbound.GetPreferencesRevisionUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.OperationalPreferencesRepository;
import br.com.brew.brassia.brewery.domain.OperationalPreferencesSnapshot;
import java.util.Objects;

public final class GetPreferencesRevisionHandler implements GetPreferencesRevisionUseCase {
    private final OperationalPreferencesRepository preferences;

    public GetPreferencesRevisionHandler(OperationalPreferencesRepository preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public OperationalPreferencesSnapshot handle(Query query) {
        return preferences.findRevision(query.breweryId(), query.version())
                .orElseThrow(() -> new IllegalArgumentException("revisão inexistente"));
    }
}
