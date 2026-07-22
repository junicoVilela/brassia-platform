package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.brewery.application.port.inbound.UpdateOperationalPreferencesUseCase;
import br.com.brew.brassia.brewery.application.port.outbound.OperationalPreferencesRepository;
import br.com.brew.brassia.brewery.domain.OperationalPreferences;
import java.util.Map;
import java.util.Objects;

public final class UpdateOperationalPreferencesHandler implements UpdateOperationalPreferencesUseCase {
    private final OperationalPreferencesRepository preferences;
    private final AuditTrail audit;

    public UpdateOperationalPreferencesHandler(OperationalPreferencesRepository preferences, AuditTrail audit) {
        this.preferences = Objects.requireNonNull(preferences);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var prefs = preferences.findByBreweryId(command.breweryId())
                .orElseGet(() -> OperationalPreferences.defaults(command.breweryId()));
        prefs.update(
                command.volumeUnit(),
                command.massUnit(),
                command.temperatureUnit(),
                command.currencyCode(),
                command.maxBatchVolume(),
                command.allowNegativeStock(),
                command.stockPolicy(),
                command.version());
        preferences.save(prefs);
        var snapshot = prefs.snapshot();
        preferences.appendRevision(snapshot, command.actorId());
        audit.record(AuditEvent.success(
                command.breweryId(),
                command.actorId(),
                "brewery.preferences.update",
                "brewery_operational_preferences",
                command.breweryId().toString(),
                Map.of(
                        "version", Long.toString(prefs.version()),
                        "currency", prefs.currencyCode(),
                        "stockPolicy", prefs.stockPolicy())));
        return new Result(
                prefs.breweryId(), prefs.volumeUnit(), prefs.massUnit(), prefs.temperatureUnit(),
                prefs.currencyCode(), prefs.maxBatchVolume(), prefs.allowNegativeStock(),
                prefs.stockPolicy(), prefs.version());
    }
}
