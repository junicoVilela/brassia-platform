package br.com.brew.brassia.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreatePhysicalCountUseCase {
    Result handle(Command command);

    record LineInput(UUID lotId, BigDecimal countedQuantity) {}

    record Command(UUID actorId, UUID breweryId, List<LineInput> lines) {}

    record Result(UUID id, String status) {}
}
