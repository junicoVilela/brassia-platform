package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Movimento imutável do ledger de estoque (STK-002). A magnitude é sempre
 * positiva; o tipo define o sinal aplicado a on-hand/reservado. Ajustes exigem
 * motivo. Referência liga o movimento à sua origem (ex.: ordem de produção).
 */
public final class StockMovement {

    private final UUID id;
    private final UUID breweryId;
    private final UUID lotId;
    private final UUID ingredientId;
    private final StockMovementType type;
    private final BigDecimal quantity;
    private final UUID reference;
    private final String reason;
    private final Instant occurredAt;
    private final UUID actorId;

    private StockMovement(UUID id, UUID breweryId, UUID lotId, UUID ingredientId, StockMovementType type,
            BigDecimal quantity, UUID reference, String reason, Instant occurredAt, UUID actorId) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.lotId = Objects.requireNonNull(lotId, "lotId");
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId");
        this.type = Objects.requireNonNull(type, "type");
        this.quantity = requirePositive(quantity);
        this.reference = reference;
        this.reason = normalizeReason(type, reason);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
    }

    public static StockMovement record(UUID breweryId, UUID lotId, UUID ingredientId, StockMovementType type,
            BigDecimal quantity, UUID reference, String reason, Instant occurredAt, UUID actorId) {
        return new StockMovement(UUID.randomUUID(), breweryId, lotId, ingredientId, type, quantity, reference,
                reason, occurredAt, actorId);
    }

    public static StockMovement reconstitute(UUID id, UUID breweryId, UUID lotId, UUID ingredientId,
            StockMovementType type, BigDecimal quantity, UUID reference, String reason, Instant occurredAt,
            UUID actorId) {
        return new StockMovement(id, breweryId, lotId, ingredientId, type, quantity, reference, reason,
                occurredAt, actorId);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("quantidade do movimento deve ser positiva");
        }
        return value;
    }

    private static String normalizeReason(StockMovementType type, String reason) {
        if (type.requiresReason() && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("ajuste exige motivo");
        }
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    /** Delta aplicado ao on-hand (assinado). */
    public BigDecimal onHandDelta() {
        return quantity.multiply(BigDecimal.valueOf(type.onHandSign()));
    }

    /** Delta aplicado ao reservado (assinado). */
    public BigDecimal reservedDelta() {
        return quantity.multiply(BigDecimal.valueOf(type.reservedSign()));
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID lotId() {
        return lotId;
    }

    public UUID ingredientId() {
        return ingredientId;
    }

    public StockMovementType type() {
        return type;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public UUID reference() {
        return reference;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID actorId() {
        return actorId;
    }
}
