package br.com.brew.brassia.brewery.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface RegisterBreweryUseCase {
    Result handle(Command command);

    /**
     * @param actorId   usuário autenticado que registra (auditoria)
     * @param code      código único da cervejaria
     * @param name      nome da cervejaria
     * @param timezone  fuso horário (IANA)
     */
    record Command(UUID actorId, String code, String name, String timezone) {}

    record Result(UUID id, String code, String name, String timezone) {}
}
