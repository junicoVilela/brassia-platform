package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Execução do envase (PKG-003): o que realmente saiu da linha.
 *
 * <p>O balanço fecha por construção: <strong>perda é derivada</strong>, não digitada. O operador
 * declara o que mediu — volume que saiu do tanque, unidades boas e rejeitadas — e a perda é o
 * resto. Aceitar perda digitada ao lado dos outros três números permitiria um balanço que não
 * fecha, e é justamente isso que esta história existe para impedir.
 *
 * <p>Rejeito consome embalagem igual: uma lata cheia e descartada é uma lata gasta. Por isso o
 * consumo de embalagem é boas + rejeitadas, não só as boas.
 */
public final class PackagingRun {

    private static final BigDecimal ML_PER_LITER = new BigDecimal("1000");
    private static final int VOLUME_SCALE = 3;

    private final UUID id;
    private final UUID planId;
    private final UUID breweryId;
    private final UUID batchId;
    private final BigDecimal containerVolumeMl;
    private final BigDecimal inputVolumeLiters;
    private final int producedUnits;
    private final int rejectedUnits;
    private final BigDecimal packagedVolumeLiters;
    private final BigDecimal rejectedVolumeLiters;
    private final BigDecimal lossesLiters;
    private final String note;
    private final Instant executedAt;
    private final UUID executedBy;

    private PackagingRun(UUID id, UUID planId, UUID breweryId, UUID batchId, BigDecimal containerVolumeMl,
            BigDecimal inputVolumeLiters, int producedUnits, int rejectedUnits, String note, Instant executedAt,
            UUID executedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.planId = Objects.requireNonNull(planId, "plano é obrigatório");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "lote é obrigatório");
        this.containerVolumeMl = requirePositive(containerVolumeMl, "volume da embalagem");
        this.inputVolumeLiters = requirePositive(inputVolumeLiters, "volume que saiu do tanque");
        if (producedUnits < 0 || rejectedUnits < 0) {
            throw new IllegalArgumentException("unidades não podem ser negativas");
        }
        if (producedUnits + rejectedUnits == 0) {
            throw new IllegalArgumentException("envase sem nenhuma unidade não é execução");
        }
        this.producedUnits = producedUnits;
        this.rejectedUnits = rejectedUnits;
        this.packagedVolumeLiters = volumeLitersOf(producedUnits, this.containerVolumeMl);
        this.rejectedVolumeLiters = volumeLitersOf(rejectedUnits, this.containerVolumeMl);
        var losses = this.inputVolumeLiters.subtract(packagedVolumeLiters).subtract(rejectedVolumeLiters);
        if (losses.signum() < 0) {
            throw new VolumeBalanceException(this.inputVolumeLiters, packagedVolumeLiters, rejectedVolumeLiters);
        }
        this.lossesLiters = losses.setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
        this.note = note == null || note.isBlank() ? null : requireText(note, "observação", 200);
        this.executedAt = Objects.requireNonNull(executedAt, "instante da execução é obrigatório");
        this.executedBy = Objects.requireNonNull(executedBy, "responsável é obrigatório");
    }

    public static PackagingRun execute(UUID planId, UUID breweryId, UUID batchId, BigDecimal containerVolumeMl,
            BigDecimal inputVolumeLiters, int producedUnits, int rejectedUnits, String note, Instant executedAt,
            UUID executedBy) {
        return new PackagingRun(UUID.randomUUID(), planId, breweryId, batchId, containerVolumeMl,
                inputVolumeLiters, producedUnits, rejectedUnits, note, executedAt, executedBy);
    }

    public static PackagingRun reconstitute(UUID id, UUID planId, UUID breweryId, UUID batchId,
            BigDecimal containerVolumeMl, BigDecimal inputVolumeLiters, int producedUnits, int rejectedUnits,
            String note, Instant executedAt, UUID executedBy) {
        return new PackagingRun(id, planId, breweryId, batchId, containerVolumeMl, inputVolumeLiters,
                producedUnits, rejectedUnits, note, executedAt, executedBy);
    }

    /** Embalagem gasta: rejeito consome embalagem igual, então entram as duas contagens. */
    public int containersConsumed() {
        return producedUnits + rejectedUnits;
    }

    /** Perda sobre o que saiu do tanque, em %. Zero quando não houve entrada medida. */
    public BigDecimal lossPercent() {
        return lossesLiters.multiply(new BigDecimal("100"))
                .divide(inputVolumeLiters, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal volumeLitersOf(int units, BigDecimal containerVolumeMl) {
        return containerVolumeMl.multiply(BigDecimal.valueOf(units))
                .divide(ML_PER_LITER, VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " é obrigatório");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " deve ser positivo");
        }
        return value;
    }

    private static String requireText(String value, String field, int max) {
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID planId() { return planId; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public BigDecimal containerVolumeMl() { return containerVolumeMl; }
    public BigDecimal inputVolumeLiters() { return inputVolumeLiters; }
    public int producedUnits() { return producedUnits; }
    public int rejectedUnits() { return rejectedUnits; }
    public BigDecimal packagedVolumeLiters() { return packagedVolumeLiters; }
    public BigDecimal rejectedVolumeLiters() { return rejectedVolumeLiters; }
    public BigDecimal lossesLiters() { return lossesLiters; }
    public String note() { return note; }
    public Instant executedAt() { return executedAt; }
    public UUID executedBy() { return executedBy; }
}
