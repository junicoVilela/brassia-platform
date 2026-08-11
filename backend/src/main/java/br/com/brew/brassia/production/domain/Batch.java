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
    private final BatchOrigin origin;
    private final String code;
    private final UUID recipeId;
    private final int recipeVersion;
    private final String recipeName;
    private final BigDecimal volumeLiters;
    private final BatchStatus status;
    private final Instant startedAt;
    private final UUID startedBy;
    private final List<BatchStep> steps;

    private Batch(BatchId id, UUID breweryId, UUID orderId, BatchOrigin origin, String code, UUID recipeId,
            int recipeVersion, String recipeName, BigDecimal volumeLiters, BatchStatus status,
            Instant startedAt, UUID startedBy, List<BatchStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.origin = Objects.requireNonNull(origin, "origin");
        // Ordem e origem andam juntas nos dois sentidos: lote de ordem sem ordem seria um lote órfão do
        // planejamento, e lote de blend com ordem faria o custeio ratear uma ordem que ninguém programou.
        if (origin == BatchOrigin.BREW_ORDER && orderId == null) {
            throw new IllegalArgumentException("lote de ordem precisa da ordem que o gerou");
        }
        if (origin == BatchOrigin.BLEND && orderId != null) {
            throw new IllegalArgumentException("lote de blend não nasce de ordem de produção");
        }
        this.orderId = orderId;
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
        return new Batch(BatchId.newId(), breweryId, orderId, BatchOrigin.BREW_ORDER, code, recipeId,
                recipeVersion, recipeName, volumeLiters, BatchStatus.IN_PROGRESS, startedAt, startedBy, steps);
    }

    /**
     * Lote produzido pela execução de um blend (DEC-BLD-003).
     *
     * <p><strong>Nasce em fermentação, e sem roteiro.</strong> Não houve dia de brassa: a cerveja já
     * existia nos lotes de origem, e o que aconteceu foi ela mudar de tanque. Um roteiro de brassa aqui
     * descreveria etapas que ninguém executou, e o estado de brassa impediria o envase — que é justamente
     * o destino desta cerveja.
     */
    public static Batch openFromBlend(UUID breweryId, String code, UUID recipeId, int recipeVersion,
            String recipeName, BigDecimal volumeLiters, Instant startedAt, UUID startedBy) {
        return new Batch(BatchId.newId(), breweryId, null, BatchOrigin.BLEND, code, recipeId, recipeVersion,
                recipeName, volumeLiters, BatchStatus.FERMENTING, startedAt, startedBy, List.of());
    }

    public static Batch reconstitute(BatchId id, UUID breweryId, UUID orderId, BatchOrigin origin, String code,
            UUID recipeId, int recipeVersion, String recipeName, BigDecimal volumeLiters, BatchStatus status,
            Instant startedAt, UUID startedBy, List<BatchStep> steps) {
        return new Batch(id, breweryId, orderId, origin, code, recipeId, recipeVersion, recipeName, volumeLiters,
                status, startedAt, startedBy, steps);
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
    public BatchOrigin origin() { return origin; }
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
