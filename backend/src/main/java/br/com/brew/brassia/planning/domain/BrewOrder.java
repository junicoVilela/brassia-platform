package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ordem de produção (BOP-001): gerada de uma receita publicada, com código único
 * e um {@link OrderSnapshot} congelado (cálculo da receita + perfil do equipamento).
 * Nasce em {@link BrewOrderStatus#DRAFT}.
 *
 * <p>Invariante: volume positivo e não superior à capacidade do equipamento do
 * snapshot. A completude do snapshot é garantida pelo próprio {@link OrderSnapshot}.
 */
public final class BrewOrder {

    private final BrewOrderId id;
    private final UUID breweryId;
    private final String code;
    private final UUID recipeId;
    private final int recipeVersion;
    private final BigDecimal volumeLiters;
    private final OrderSnapshot snapshot;
    private final BrewOrderStatus status;
    private final UUID assignedUserId;
    private final Instant releasedAt;
    private final long version;

    private BrewOrder(BrewOrderId id, UUID breweryId, String code, UUID recipeId, int recipeVersion,
            BigDecimal volumeLiters, OrderSnapshot snapshot, BrewOrderStatus status, UUID assignedUserId,
            Instant releasedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireCode(code);
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.recipeVersion = recipeVersion;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.volumeLiters = requireWithinCapacity(volumeLiters, snapshot);
        this.status = Objects.requireNonNull(status, "status");
        this.assignedUserId = assignedUserId;
        this.releasedAt = releasedAt;
        this.version = version;
    }

    public static BrewOrder create(UUID breweryId, String code, UUID recipeId, int recipeVersion,
            BigDecimal volumeLiters, OrderSnapshot snapshot) {
        return new BrewOrder(BrewOrderId.newId(), breweryId, code, recipeId, recipeVersion, volumeLiters,
                snapshot, BrewOrderStatus.DRAFT, null, null, 1);
    }

    public static BrewOrder reconstitute(BrewOrderId id, UUID breweryId, String code, UUID recipeId,
            int recipeVersion, BigDecimal volumeLiters, OrderSnapshot snapshot, BrewOrderStatus status,
            UUID assignedUserId, Instant releasedAt, long version) {
        return new BrewOrder(id, breweryId, code, recipeId, recipeVersion, volumeLiters, snapshot, status,
                assignedUserId, releasedAt, version);
    }

    /** Só uma OP em rascunho pode ser liberada (BOP-002). */
    public boolean releasable() {
        return status == BrewOrderStatus.DRAFT;
    }

    /**
     * Libera a OP (DRAFT → RELEASED) sob um responsável. Regra de transição do
     * domínio; os demais bloqueios (equipamento, estoque, sanitização) são
     * verificados no caso de uso antes de chamar este método.
     */
    public BrewOrder release(UUID assignedUserId, Instant at) {
        if (!releasable()) {
            throw new IllegalStateException("ordem não está em rascunho");
        }
        Objects.requireNonNull(assignedUserId, "responsável é obrigatório para liberar");
        Objects.requireNonNull(at, "at");
        return new BrewOrder(id, breweryId, code, recipeId, recipeVersion, volumeLiters, snapshot,
                BrewOrderStatus.RELEASED, assignedUserId, at, version + 1);
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("código da OP é obrigatório");
        }
        return code;
    }

    private static BigDecimal requireWithinCapacity(BigDecimal volume, OrderSnapshot snapshot) {
        if (volume == null || volume.signum() <= 0) {
            throw new IllegalArgumentException("volume deve ser positivo");
        }
        var capacity = snapshot.equipment().capacityLiters();
        if (capacity != null && volume.compareTo(capacity) > 0) {
            throw new IllegalArgumentException("volume excede a capacidade do equipamento");
        }
        return volume;
    }

    public BrewOrderId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public int recipeVersion() {
        return recipeVersion;
    }

    public BigDecimal volumeLiters() {
        return volumeLiters;
    }

    public OrderSnapshot snapshot() {
        return snapshot;
    }

    public BrewOrderStatus status() {
        return status;
    }

    public UUID assignedUserId() {
        return assignedUserId;
    }

    public Instant releasedAt() {
        return releasedAt;
    }

    public long version() {
        return version;
    }
}
