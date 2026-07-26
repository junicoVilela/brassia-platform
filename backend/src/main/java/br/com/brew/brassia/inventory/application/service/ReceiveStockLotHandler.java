package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.StockInspection;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.purchasing.SupplierLookup;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Recebe um lote de insumo (STK-001): valida ingrediente (catálogo) e fornecedor
 * (compras), registra o lote e audita. Lote bloqueado não fica disponível.
 */
public final class ReceiveStockLotHandler implements ReceiveStockLotUseCase {

    private final StockLotRepository repository;
    private final IngredientSpecLookup ingredients;
    private final SupplierLookup suppliers;
    private final AuditTrail audit;

    public ReceiveStockLotHandler(StockLotRepository repository, IngredientSpecLookup ingredients,
            SupplierLookup suppliers, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.suppliers = Objects.requireNonNull(suppliers);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (ingredients.find(command.breweryId(), command.ingredientId()).isEmpty()) {
            throw new IllegalArgumentException("ingrediente inexistente");
        }
        if (!suppliers.exists(command.breweryId(), command.supplierId())) {
            throw new IllegalArgumentException("fornecedor inexistente");
        }

        var lot = StockLot.receive(command.breweryId(), command.ingredientId(), command.supplierId(),
                command.supplierLotCode(), command.quantity(), StockUnit.of(command.unit()), command.unitCost(),
                command.expiryDate(), Instant.now(), StockInspection.of(command.inspection()));
        repository.insert(lot);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.lot.receive",
                "inventory.lot", lot.id().value().toString(),
                Map.of("ingredientId", command.ingredientId().toString(),
                        "supplierId", command.supplierId().toString(),
                        "inspection", lot.inspection().name())));

        return new Result(lot.id().value(), lot.available());
    }
}
