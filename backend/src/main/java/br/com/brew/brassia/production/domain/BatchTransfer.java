package br.com.brew.brassia.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transferência do lote ao fermentador (PRD-005): volume transferido, OG, perdas e
 * fermentador destino. Uma por lote. Capacidade do destino e balanço de massa são
 * verificados no caso de uso; aqui garantem-se os invariantes de valores.
 */
public final class BatchTransfer {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final UUID destinationEquipmentId;
    private final TransferKind kind;
    private final BigDecimal volumeLiters;
    private final BigDecimal ogSg;
    private final BigDecimal lossesLiters;
    private final Instant transferredAt;
    private final UUID transferredBy;

    private BatchTransfer(UUID id, UUID breweryId, UUID batchId, UUID destinationEquipmentId,
            TransferKind kind, BigDecimal volumeLiters, BigDecimal ogSg, BigDecimal lossesLiters,
            Instant transferredAt, UUID transferredBy) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.destinationEquipmentId = Objects.requireNonNull(destinationEquipmentId, "destinationEquipmentId");
        this.volumeLiters = requirePositive(volumeLiters, "volume transferido");
        // OG e tipo andam juntos nos dois sentidos. Exigir OG do enchimento de blend obrigaria a inventar
        // uma densidade que ninguém mediu; aceitar transferência de brassa sem OG perderia a medição que
        // o acompanhamento da fermentação usa como ponto de partida.
        this.ogSg = kind == TransferKind.BREW_TRANSFER ? requirePositive(ogSg, "OG") : requireAbsent(ogSg);
        this.lossesLiters = requireNonNegative(lossesLiters);
        this.transferredAt = Objects.requireNonNull(transferredAt, "transferredAt");
        this.transferredBy = Objects.requireNonNull(transferredBy, "transferredBy");
    }

    public static BatchTransfer record(UUID breweryId, UUID batchId, UUID destinationEquipmentId,
            BigDecimal volumeLiters, BigDecimal ogSg, BigDecimal lossesLiters, Instant transferredAt,
            UUID transferredBy) {
        return new BatchTransfer(UUID.randomUUID(), breweryId, batchId, destinationEquipmentId,
                TransferKind.BREW_TRANSFER, volumeLiters, ogSg, lossesLiters, transferredAt, transferredBy);
    }

    /**
     * O tanque que recebeu o lote produzido por um blend (DEC-BLD-003).
     *
     * <p>Sem perdas declaradas: a perda do blend já foi declarada na operação, e repeti-la aqui a contaria
     * duas vezes.
     */
    public static BatchTransfer fillFromBlend(UUID breweryId, UUID batchId, UUID destinationEquipmentId,
            BigDecimal volumeLiters, Instant transferredAt, UUID transferredBy) {
        return new BatchTransfer(UUID.randomUUID(), breweryId, batchId, destinationEquipmentId,
                TransferKind.BLEND_FILL, volumeLiters, null, BigDecimal.ZERO, transferredAt, transferredBy);
    }

    public static BatchTransfer reconstitute(UUID id, UUID breweryId, UUID batchId, UUID destinationEquipmentId,
            TransferKind kind, BigDecimal volumeLiters, BigDecimal ogSg, BigDecimal lossesLiters,
            Instant transferredAt, UUID transferredBy) {
        return new BatchTransfer(id, breweryId, batchId, destinationEquipmentId, kind, volumeLiters, ogSg,
                lossesLiters, transferredAt, transferredBy);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo");
        }
        return value;
    }

    private static BigDecimal requireAbsent(BigDecimal value) {
        if (value != null) {
            throw new IllegalArgumentException("enchimento de blend não tem OG: ninguém mediu");
        }
        return null;
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("perdas não podem ser negativas");
        }
        return value;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public UUID destinationEquipmentId() { return destinationEquipmentId; }
    public TransferKind kind() { return kind; }
    public BigDecimal volumeLiters() { return volumeLiters; }
    public BigDecimal ogSg() { return ogSg; }
    public BigDecimal lossesLiters() { return lossesLiters; }
    public Instant transferredAt() { return transferredAt; }
    public UUID transferredBy() { return transferredBy; }
}
