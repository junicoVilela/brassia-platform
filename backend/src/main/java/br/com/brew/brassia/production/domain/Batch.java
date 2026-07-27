package br.com.brew.brassia.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lote de produção (PRD-001): criado ao iniciar uma OP liberada. Guarda a
 * referência 1:1 à OP, o snapshot da receita (nome + versão, congelados) e o
 * roteiro do dia de brassa. Nasce em {@link BatchStatus#IN_PROGRESS}.
 */
public final class Batch {

    private final BatchId id;
    private final UUID breweryId;
    private final UUID orderId;
    private final String code;
    private final UUID recipeId;
    private final int recipeVersion;
    private final String recipeName;
    private final BigDecimal volumeLiters;
    private final BatchStatus status;
    private final Instant startedAt;
    private final UUID startedBy;
    private final List<BatchStep> steps;

    private Batch(BatchId id, UUID breweryId, UUID orderId, String code, UUID recipeId, int recipeVersion,
            String recipeName, BigDecimal volumeLiters, BatchStatus status, Instant startedAt, UUID startedBy,
            List<BatchStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.code = requireText(code, "código");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.recipeVersion = recipeVersion;
        this.recipeName = requireText(recipeName, "nome da receita");
        this.volumeLiters = Objects.requireNonNull(volumeLiters, "volumeLiters");
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.startedBy = Objects.requireNonNull(startedBy, "startedBy");
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }

    public static Batch open(UUID breweryId, UUID orderId, String code, UUID recipeId, int recipeVersion,
            String recipeName, BigDecimal volumeLiters, Instant startedAt, UUID startedBy, List<BatchStep> steps) {
        return new Batch(BatchId.newId(), breweryId, orderId, code, recipeId, recipeVersion, recipeName,
                volumeLiters, BatchStatus.IN_PROGRESS, startedAt, startedBy, steps);
    }

    public static Batch reconstitute(BatchId id, UUID breweryId, UUID orderId, String code, UUID recipeId,
            int recipeVersion, String recipeName, BigDecimal volumeLiters, BatchStatus status, Instant startedAt,
            UUID startedBy, List<BatchStep> steps) {
        return new Batch(id, breweryId, orderId, code, recipeId, recipeVersion, recipeName, volumeLiters, status,
                startedAt, startedBy, steps);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }

    public BatchId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID orderId() { return orderId; }
    public String code() { return code; }
    public UUID recipeId() { return recipeId; }
    public int recipeVersion() { return recipeVersion; }
    public String recipeName() { return recipeName; }
    public BigDecimal volumeLiters() { return volumeLiters; }
    public BatchStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public UUID startedBy() { return startedBy; }
    public List<BatchStep> steps() { return steps; }
}
