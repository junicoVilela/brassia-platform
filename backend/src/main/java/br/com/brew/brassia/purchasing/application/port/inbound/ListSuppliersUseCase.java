package br.com.brew.brassia.purchasing.application.port.inbound;

import br.com.brew.brassia.purchasing.domain.Supplier;
import java.util.List;
import java.util.UUID;

public interface ListSuppliersUseCase {
    List<Supplier> handle(UUID breweryId);
}
