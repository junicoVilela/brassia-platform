package br.com.brew.brassia.inventory.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vincula valores medidos a um lote (STK-005): manual, importado ou sugerido.
 * Write-once por propriedade — regravar é conflito. Não altera o catálogo.
 */
public interface RecordLotPropertiesUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID lotId, List<PropertyInput> properties) {}

    record PropertyInput(String property, BigDecimal value, String unit, String source, String confidence) {}

    record Result(List<UUID> ids) {}
}
