package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
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
    private final long version;

    private BrewOrder(BrewOrderId id, UUID breweryId, String code, UUID recipeId, int recipeVersion,
            BigDecimal volumeLiters, OrderSnapshot snapshot, BrewOrderStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireCode(code);
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.recipeVersion = recipeVersion;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.volumeLiters = requireWithinCapacity(volumeLiters, snapshot);
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public static BrewOrder create(UUID breweryId, String code, UUID recipeId, int recipeVersion,
            BigDecimal volumeLiters, OrderSnapshot snapshot) {
        return new BrewOrder(BrewOrderId.newId(), breweryId, code, recipeId, recipeVersion, volumeLiters,
                snapshot, BrewOrderStatus.DRAFT, 1);
    }

    public static BrewOrder reconstitute(BrewOrderId id, UUID breweryId, String code, UUID recipeId,
            int recipeVersion, BigDecimal volumeLiters, OrderSnapshot snapshot, BrewOrderStatus status, long version) {
        return new BrewOrder(id, breweryId, code, recipeId, recipeVersion, volumeLiters, snapshot, status, version);
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

    public long version() {
        return version;
    }
}
