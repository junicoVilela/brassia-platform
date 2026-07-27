package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.UUID;

/** Cadastra uma regra da matriz de compatibilidade (CLN-002). */
public interface CreateRuleUseCase {
    UUID handle(Command command);

    record Command(UUID actorId, UUID breweryId, String material, String soiling, String risk,
            String previousProduct, String procedureCode, String method, String alternative, String restriction) {}
}
