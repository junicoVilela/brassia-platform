package br.com.brew.brassia.inventory.application.port.inbound;

import br.com.brew.brassia.inventory.domain.PhysicalCount;
import java.util.List;
import java.util.UUID;

/** Consultas de contagem física. */
public interface PhysicalCountQueries {
    PhysicalCount get(UUID breweryId, UUID countId);

    List<PhysicalCount> list(UUID breweryId);
}
