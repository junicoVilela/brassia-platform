package br.com.brew.brassia.purchasing.application.port.inbound;

import java.util.UUID;

public interface RegisterSupplierUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String name, String code, Integer leadTimeDays) {}

    record Result(UUID id, String name, String code, Integer leadTimeDays) {}
}
