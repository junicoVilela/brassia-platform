package br.com.brew.brassia.brewery.application.port.outbound;

import br.com.brew.brassia.brewery.domain.OperationalPreferences;
import br.com.brew.brassia.brewery.domain.OperationalPreferencesSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface OperationalPreferencesRepository {
    Optional<OperationalPreferences> findByBreweryId(UUID breweryId);

    void save(OperationalPreferences preferences);

    /** Persiste revisão imutável da versão corrente (após save). */
    void appendRevision(OperationalPreferencesSnapshot snapshot, UUID recordedBy);

    Optional<OperationalPreferencesSnapshot> findRevision(UUID breweryId, long version);
}
