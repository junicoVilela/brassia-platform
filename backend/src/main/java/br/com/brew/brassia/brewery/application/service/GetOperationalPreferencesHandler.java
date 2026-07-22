package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.brewery.application.port.inbound.GetOperationalPreferencesUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.OperationalPreferencesRepository;
import br.com.brew.brassia.brewery.domain.OperationalPreferences;
import java.util.Objects;

public final class GetOperationalPreferencesHandler implements GetOperationalPreferencesUseCase {
    private final OperationalPreferencesRepository preferences;

    public GetOperationalPreferencesHandler(OperationalPreferencesRepository preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public Result handle(Query query) {
        var prefs = preferences.findByBreweryId(query.breweryId())
                .orElseGet(() -> {
                    var defaults = OperationalPreferences.defaults(query.breweryId());
                    preferences.save(defaults);
                    preferences.appendRevision(defaults.snapshot(), null);
                    return defaults;
                });
        return new Result(
                prefs.breweryId(), prefs.volumeUnit(), prefs.massUnit(), prefs.temperatureUnit(),
                prefs.currencyCode(), prefs.maxBatchVolume(), prefs.allowNegativeStock(),
                prefs.stockPolicy(), prefs.version());
    }
}
