package br.com.brew.brassia.brewery.application.service;

import br.com.brew.brassia.brewery.BreweryCurrencyLookup;
import br.com.brew.brassia.brewery.application.port.outbound.OperationalPreferencesRepository;
import br.com.brew.brassia.brewery.domain.OperationalPreferences;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implementa a porta de moeda a partir das preferências operacionais (DEB-SAL-001). */
public class BreweryCurrencyService implements BreweryCurrencyLookup {

    private final OperationalPreferencesRepository preferences;

    public BreweryCurrencyService(OperationalPreferencesRepository preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    @Transactional(readOnly = true)
    public String currencyOf(UUID breweryId) {
        // Leitura pura: o caso de uso das preferências GRAVA os padrões ao responder pela primeira vez, e
        // uma consulta de custo não pode ter esse efeito colateral. O padrão vem do mesmo lugar que ele
        // usaria, e não de uma constante inventada aqui.
        return preferences.findByBreweryId(breweryId)
                .map(OperationalPreferences::currencyCode)
                .orElseGet(() -> OperationalPreferences.defaults(breweryId).currencyCode());
    }
}
