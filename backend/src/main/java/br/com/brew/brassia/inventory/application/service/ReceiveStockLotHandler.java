package br.com.brew.brassia.inventory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.inventory.application.port.inbound.ReceiveStockLotUseCase;
import br.com.brew.brassia.inventory.application.port.outbound.StockLedgerRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotPropertyRepository;
import br.com.brew.brassia.inventory.application.port.outbound.StockLotRepository;
import br.com.brew.brassia.inventory.domain.LotPropertyConfidence;
import br.com.brew.brassia.inventory.domain.LotPropertySource;
import br.com.brew.brassia.inventory.domain.StockInspection;
import br.com.brew.brassia.inventory.domain.StockLot;
import br.com.brew.brassia.inventory.domain.StockLotProperty;
import br.com.brew.brassia.inventory.domain.StockMovement;
import br.com.brew.brassia.inventory.domain.StockMovementType;
import br.com.brew.brassia.inventory.domain.StockUnit;
import br.com.brew.brassia.purchasing.SupplierLookup;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Recebe um lote de insumo (STK-001): valida ingrediente (catálogo) e fornecedor
 * (compras), registra o lote e audita. Lote bloqueado não fica disponível.
 */
public final class ReceiveStockLotHandler implements ReceiveStockLotUseCase {

    private final StockLotRepository repository;
    private final StockLedgerRepository ledger;
    private final StockLotPropertyRepository lotProperties;
    private final IngredientSpecLookup ingredients;
    private final SupplierLookup suppliers;
    private final AuditTrail audit;

    public ReceiveStockLotHandler(StockLotRepository repository, StockLedgerRepository ledger,
            StockLotPropertyRepository lotProperties, IngredientSpecLookup ingredients, SupplierLookup suppliers,
            AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.ledger = Objects.requireNonNull(ledger);
        this.lotProperties = Objects.requireNonNull(lotProperties);
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

        // O saldo deriva do ledger: o recebimento lança a ENTRY inicial no mesmo commit.
        ledger.append(StockMovement.record(lot.breweryId(), lot.id().value(), lot.ingredientId(),
                StockMovementType.ENTRY, lot.receivedQuantity(), null, null, lot.receivedAt(), command.actorId()));

        // STK-005: valores medidos opcionais vinculados no mesmo commit (write-once por propriedade).
        if (command.properties() != null && !command.properties().isEmpty()) {
            var seen = new HashSet<String>();
            for (var input : command.properties()) {
                var property = StockLotProperty.record(lot.id().value(), lot.breweryId(), input.property(),
                        input.value(), input.unit(), LotPropertySource.of(input.source()),
                        LotPropertyConfidence.of(input.confidence()), lot.receivedAt(), command.actorId());
                if (!seen.add(property.property())) {
                    throw new IllegalArgumentException("propriedade duplicada na requisição: " + property.property());
                }
                lotProperties.insert(property);
            }
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "inventory.lot.receive",
                "inventory.lot", lot.id().value().toString(),
                Map.of("ingredientId", command.ingredientId().toString(),
                        "supplierId", command.supplierId().toString(),
                        "inspection", lot.inspection().name())));

        return new Result(lot.id().value(), lot.available());
    }
}
