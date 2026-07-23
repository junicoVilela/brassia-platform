package br.com.brew.brassia.brewery.application.port.inbound;

import br.com.brew.brassia.brewery.domain.OperationalPreferencesSnapshot;
import java.util.UUID;

/** Consulta revisão imutável — prova de que mudança atual não altera o passado. */
@FunctionalInterface
public interface PreferencesRevisionUseCase {
    OperationalPreferencesSnapshot handle(Query query);

    record Query(UUID breweryId, long version) {}
}
