package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import br.com.brew.brassia.brewery.application.port.inbound.GetOperationalPreferencesUseCase;
import br.com.brew.brassia.brewery.application.port.inbound.UpdateOperationalPreferencesUseCase;
import br.com.brew.brassia.brewery.domain.OperationalPreferencesSnapshot;
import java.math.BigDecimal;
import java.util.UUID;

public record PreferencesResponse(
        UUID breweryId,
        String volumeUnit,
        String massUnit,
        String temperatureUnit,
        String currencyCode,
        BigDecimal maxBatchVolume,
        boolean allowNegativeStock,
        String stockPolicy,
        long version) {

    public static PreferencesResponse from(GetOperationalPreferencesUseCase.Result r) {
        return new PreferencesResponse(
                r.breweryId(), r.volumeUnit(), r.massUnit(), r.temperatureUnit(), r.currencyCode(),
                r.maxBatchVolume(), r.allowNegativeStock(), r.stockPolicy(), r.version());
    }

    public static PreferencesResponse from(UpdateOperationalPreferencesUseCase.Result r) {
        return new PreferencesResponse(
                r.breweryId(), r.volumeUnit(), r.massUnit(), r.temperatureUnit(), r.currencyCode(),
                r.maxBatchVolume(), r.allowNegativeStock(), r.stockPolicy(), r.version());
    }

    /** Revisão histórica imutável: a versão vem do snapshot preservado. */
    public static PreferencesResponse from(OperationalPreferencesSnapshot snap) {
        return new PreferencesResponse(
                snap.breweryId(), snap.volumeUnit(), snap.massUnit(), snap.temperatureUnit(),
                snap.currencyCode(), snap.maxBatchVolume(), snap.allowNegativeStock(),
                snap.stockPolicy(), snap.preferenceVersion());
    }
}
