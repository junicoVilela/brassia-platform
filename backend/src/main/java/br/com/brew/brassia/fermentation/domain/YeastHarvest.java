package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Coleta de levedura (YST-001): origem, geração, condição, viabilidade e armazenamento.
 *
 * <p>A geração é <strong>derivada</strong> da coleta-mãe (sem mãe = levedura comprada, geração 1),
 * para genealogia e geração nunca divergirem. A coleta nasce em quarentena: aprovar ou reprovar
 * é decisão humana, registrada com motivo e terminal — levedura reprovada (contaminação, odor,
 * viabilidade baixa) não fica disponível para reúso.
 */
public final class YeastHarvest {

    private static final BigDecimal MIN_VIABILITY = BigDecimal.ZERO;
    private static final BigDecimal MAX_VIABILITY = new BigDecimal("100");

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final UUID strainId;
    private final UUID sourceBatchId;
    private final UUID parentHarvestId;
    private final int generation;
    private final Instant harvestedAt;
    private final BigDecimal viabilityPercent;
    private final String condition;
    private final String storageLocation;
    private final BigDecimal storageTempC;
    private YeastHarvestStatus status;
    private String reviewNote;
    private Instant reviewedAt;
    private UUID reviewedBy;
    private UUID pitchedBatchId;
    private Instant pitchedAt;

    private YeastHarvest(UUID id, UUID breweryId, String code, UUID strainId, UUID sourceBatchId,
            UUID parentHarvestId, int generation, Instant harvestedAt, BigDecimal viabilityPercent, String condition,
            String storageLocation, BigDecimal storageTempC, YeastHarvestStatus status, String reviewNote,
            Instant reviewedAt, UUID reviewedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.strainId = Objects.requireNonNull(strainId, "cepa é obrigatória");
        this.sourceBatchId = Objects.requireNonNull(sourceBatchId, "lote de origem é obrigatório");
        this.parentHarvestId = parentHarvestId;
        if (generation < 1) {
            throw new IllegalArgumentException("geração deve ser positiva");
        }
        this.generation = generation;
        this.harvestedAt = Objects.requireNonNull(harvestedAt, "instante da coleta é obrigatório");
        this.viabilityPercent = requireViability(viabilityPercent);
        this.condition = requireText(condition, "condição", 200);
        this.storageLocation = requireText(storageLocation, "local de armazenamento", 120);
        this.storageTempC = Objects.requireNonNull(storageTempC, "temperatura de armazenamento é obrigatória");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
    }

    /**
     * Registra uma coleta em quarentena. {@code parentGeneration} nulo significa levedura
     * comprada (geração 1); com coleta-mãe, a geração é a dela mais um.
     */
    public static YeastHarvest collect(UUID breweryId, String code, UUID strainId, UUID sourceBatchId,
            UUID parentHarvestId, Integer parentGeneration, Instant harvestedAt, BigDecimal viabilityPercent,
            String condition, String storageLocation, BigDecimal storageTempC) {
        if (parentHarvestId == null && parentGeneration != null) {
            throw new IllegalArgumentException("geração da mãe sem coleta-mãe");
        }
        if (parentHarvestId != null && parentGeneration == null) {
            throw new IllegalArgumentException("coleta-mãe sem geração conhecida");
        }
        var generation = parentGeneration == null ? 1 : parentGeneration + 1;
        return new YeastHarvest(UUID.randomUUID(), breweryId, code, strainId, sourceBatchId, parentHarvestId,
                generation, harvestedAt, viabilityPercent, condition, storageLocation, storageTempC,
                YeastHarvestStatus.QUARANTINE, null, null, null);
    }

    public static YeastHarvest reconstitute(UUID id, UUID breweryId, String code, UUID strainId, UUID sourceBatchId,
            UUID parentHarvestId, int generation, Instant harvestedAt, BigDecimal viabilityPercent, String condition,
            String storageLocation, BigDecimal storageTempC, YeastHarvestStatus status, String reviewNote,
            Instant reviewedAt, UUID reviewedBy, UUID pitchedBatchId, Instant pitchedAt) {
        var harvest = new YeastHarvest(id, breweryId, code, strainId, sourceBatchId, parentHarvestId, generation,
                harvestedAt, viabilityPercent, condition, storageLocation, storageTempC, status, reviewNote,
                reviewedAt, reviewedBy);
        harvest.pitchedBatchId = pitchedBatchId;
        harvest.pitchedAt = pitchedAt;
        return harvest;
    }

    /** Libera a coleta para reúso. Só de quarentena: a revisão é terminal. */
    public void approve(UUID actorId, String note, Instant at) {
        review(YeastHarvestStatus.APPROVED, actorId, note, at);
    }

    /** Reprova a coleta (contaminação, odor, viabilidade baixa); motivo é obrigatório. */
    public void reject(UUID actorId, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("motivo da reprovação é obrigatório");
        }
        review(YeastHarvestStatus.REJECTED, actorId, reason, at);
    }

    private void review(YeastHarvestStatus target, UUID actorId, String note, Instant at) {
        if (status != YeastHarvestStatus.QUARANTINE) {
            throw new IllegalStateException("coleta já revisada: " + status);
        }
        this.status = target;
        this.reviewNote = note == null || note.isBlank() ? null : requireText(note, "parecer", 200);
        this.reviewedBy = Objects.requireNonNull(actorId, "revisor é obrigatório");
        this.reviewedAt = Objects.requireNonNull(at, "instante da revisão é obrigatório");
    }

    /**
     * Confirma o uso da coleta num lote (YST-002). Consome: a coleta sai de circulação
     * vinculada ao destino, para a mesma levedura não ser pitchada duas vezes.
     */
    public void useIn(UUID batchId, Instant at) {
        if (status != YeastHarvestStatus.APPROVED) {
            throw new IllegalStateException("coleta não está disponível para uso: " + status);
        }
        this.pitchedBatchId = Objects.requireNonNull(batchId, "lote de destino é obrigatório");
        this.pitchedAt = Objects.requireNonNull(at, "instante do uso é obrigatório");
        this.status = YeastHarvestStatus.USED;
    }

    /** Só coleta aprovada pode ser reutilizada ou virar mãe de outra. */
    public boolean available() {
        return status.available();
    }

    private static BigDecimal requireViability(BigDecimal value) {
        Objects.requireNonNull(value, "viabilidade é obrigatória");
        if (value.compareTo(MIN_VIABILITY) < 0 || value.compareTo(MAX_VIABILITY) > 0) {
            throw new IllegalArgumentException("viabilidade deve estar entre 0 e 100%");
        }
        return value;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public UUID strainId() { return strainId; }
    public UUID sourceBatchId() { return sourceBatchId; }
    public UUID parentHarvestId() { return parentHarvestId; }
    public int generation() { return generation; }
    public Instant harvestedAt() { return harvestedAt; }
    public BigDecimal viabilityPercent() { return viabilityPercent; }
    public String condition() { return condition; }
    public String storageLocation() { return storageLocation; }
    public BigDecimal storageTempC() { return storageTempC; }
    public YeastHarvestStatus status() { return status; }
    public String reviewNote() { return reviewNote; }
    public Instant reviewedAt() { return reviewedAt; }
    public UUID reviewedBy() { return reviewedBy; }
    public UUID pitchedBatchId() { return pitchedBatchId; }
    public Instant pitchedAt() { return pitchedAt; }
}
