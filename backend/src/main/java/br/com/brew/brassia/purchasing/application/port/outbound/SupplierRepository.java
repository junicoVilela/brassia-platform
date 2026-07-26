package br.com.brew.brassia.purchasing.application.port.outbound;

import br.com.brew.brassia.purchasing.domain.Supplier;
import java.util.List;
import java.util.UUID;

public interface SupplierRepository {
    boolean existsByCode(UUID breweryId, String code);

    void insert(Supplier supplier);

    List<Supplier> findAll(UUID breweryId);

    boolean exists(UUID breweryId, UUID supplierId);
}
