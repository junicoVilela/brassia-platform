package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import java.util.UUID;

/** Recomenda o método/POP para um contexto de limpeza (CLN-002). */
public interface RecommendUseCase {
    CompatibilityRule handle(Command command);

    record Command(UUID breweryId, String material, String soiling, String risk, String previousProduct) {}
}
