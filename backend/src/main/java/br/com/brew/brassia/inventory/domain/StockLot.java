package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote de insumo recebido (STK-001): fornecedor, validade, quantidade recebida,
 * custo unitário e inspeção. Invariantes: quantidade positiva, custo não negativo.
 * Um lote {@code BLOCKED} não fica disponível para reserva/consumo.
 *
 * <p>A quantidade recebida é um fato do recebimento (a entrada inicial); o saldo
 * disponível derivará do ledger de movimentos (STK-002).
 */
public final class StockLot {

    private final StockLotId id;
    private final UUID breweryId;
    private final UUID ingredientId;
    private final UUID supplierId;
    private final String supplierLotCode;
    private final BigDecimal receivedQuantity;
    private final StockUnit unit;
    private final BigDecimal unitCost;
    private final LocalDate expiryDate;
    private final Instant receivedAt;
    private final StockInspection inspection;
    private final long version;

    private StockLot(StockLotId id, UUID breweryId, UUID ingredientId, UUID supplierId, String supplierLotCode,
            BigDecimal receivedQuantity, StockUnit unit, BigDecimal unitCost, LocalDate expiryDate,
            Instant receivedAt, StockInspection inspection, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId");
        this.supplierId = Objects.requireNonNull(supplierId, "supplierId");
        this.supplierLotCode = supplierLotCode == null || supplierLotCode.isBlank() ? null : supplierLotCode.trim();
        this.receivedQuantity = requirePositive(receivedQuantity);
        this.unit = Objects.requireNonNull(unit, "unit");
        this.unitCost = requireNonNegative(unitCost);
        this.expiryDate = expiryDate;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        this.version = version;
    }

    public static StockLot receive(UUID breweryId, UUID ingredientId, UUID supplierId, String supplierLotCode,
            BigDecimal receivedQuantity, StockUnit unit, BigDecimal unitCost, LocalDate expiryDate,
            Instant receivedAt, StockInspection inspection) {
        return new StockLot(StockLotId.newId(), breweryId, ingredientId, supplierId, supplierLotCode,
                receivedQuantity, unit, unitCost, expiryDate, receivedAt, inspection, 1);
    }

    public static StockLot reconstitute(StockLotId id, UUID breweryId, UUID ingredientId, UUID supplierId,
            String supplierLotCode, BigDecimal receivedQuantity, StockUnit unit, BigDecimal unitCost,
            LocalDate expiryDate, Instant receivedAt, StockInspection inspection, long version) {
        return new StockLot(id, breweryId, ingredientId, supplierId, supplierLotCode, receivedQuantity, unit,
                unitCost, expiryDate, receivedAt, inspection, version);
    }

    /** Lote bloqueado não fica disponível (STK-001). */
    public boolean available() {
        return inspection == StockInspection.APPROVED;
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("quantidade recebida deve ser positiva");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("custo unitário não pode ser negativo");
        }
        return value;
    }

    public StockLotId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID ingredientId() {
        return ingredientId;
    }

    public UUID supplierId() {
        return supplierId;
    }

    public String supplierLotCode() {
        return supplierLotCode;
    }

    public BigDecimal receivedQuantity() {
        return receivedQuantity;
    }

    public StockUnit unit() {
        return unit;
    }

    public BigDecimal unitCost() {
        return unitCost;
    }

    public LocalDate expiryDate() {
        return expiryDate;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public StockInspection inspection() {
        return inspection;
    }

    public long version() {
        return version;
    }
}
