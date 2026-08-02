package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Plano de envase (PKG-001): embalagem, quantidade, linha, janela e checklist.
 *
 * <p>O plano é intenção, não execução — quem registra unidades e perdas é PKG-003. O volume
 * planejado é <strong>derivado</strong> de unidades × volume da embalagem, nunca informado, para
 * quantidade e volume não divergirem, e não pode ultrapassar o volume do lote de origem.
 *
 * <p>Reservar é o passo que compromete recursos: exige checklist completo e é o único caminho
 * para {@code RESERVED}. O cancelamento é terminal e devolve a embalagem reservada.
 */
public final class PackagingPlan {

    private static final BigDecimal ML_PER_LITER = new BigDecimal("1000");
    private static final int VOLUME_SCALE = 3;

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final UUID batchId;
    private final UUID containerId;
    private final BigDecimal containerVolumeMl;
    private final int plannedUnits;
    private final BigDecimal plannedVolumeLiters;
    private final UUID lineEquipmentId;
    private final Instant plannedStart;
    private final Instant plannedEnd;
    private final Map<ChecklistItem, Confirmation> checklist;
    private PackagingPlanStatus status;
    private Instant reservedAt;
    private UUID reservedBy;
    private String cancelReason;
    private Instant cancelledAt;
    private final long version;

    private PackagingPlan(UUID id, UUID breweryId, String code, UUID batchId, UUID containerId,
            BigDecimal containerVolumeMl, int plannedUnits, UUID lineEquipmentId, Instant plannedStart,
            Instant plannedEnd, PackagingPlanStatus status, Map<ChecklistItem, Confirmation> checklist,
            Instant reservedAt, UUID reservedBy, String cancelReason, Instant cancelledAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.batchId = Objects.requireNonNull(batchId, "lote é obrigatório");
        this.containerId = Objects.requireNonNull(containerId, "embalagem é obrigatória");
        this.containerVolumeMl = requirePositive(containerVolumeMl, "volume da embalagem");
        if (plannedUnits <= 0) {
            throw new IllegalArgumentException("quantidade de unidades deve ser positiva");
        }
        this.plannedUnits = plannedUnits;
        this.lineEquipmentId = Objects.requireNonNull(lineEquipmentId, "linha de envase é obrigatória");
        this.plannedStart = Objects.requireNonNull(plannedStart, "início planejado é obrigatório");
        this.plannedEnd = Objects.requireNonNull(plannedEnd, "fim planejado é obrigatório");
        if (!plannedEnd.isAfter(plannedStart)) {
            throw new IllegalArgumentException("fim planejado deve ser posterior ao início");
        }
        this.plannedVolumeLiters = volumeLitersOf(plannedUnits, this.containerVolumeMl);
        this.status = Objects.requireNonNull(status, "status");
        this.checklist = new EnumMap<>(Objects.requireNonNull(checklist, "checklist"));
        this.reservedAt = reservedAt;
        this.reservedBy = reservedBy;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
        this.version = version;
    }

    /**
     * Abre um plano de envase. {@code batchVolumeLiters} é o volume disponível do lote de origem:
     * planejar mais do que existe no tanque é erro de planejamento, não sobra a descobrir no envase.
     */
    public static PackagingPlan plan(UUID breweryId, String code, UUID batchId, UUID containerId,
            BigDecimal containerVolumeMl, int plannedUnits, UUID lineEquipmentId, Instant plannedStart,
            Instant plannedEnd, BigDecimal batchVolumeLiters) {
        var plan = new PackagingPlan(UUID.randomUUID(), breweryId, code, batchId, containerId, containerVolumeMl,
                plannedUnits, lineEquipmentId, plannedStart, plannedEnd, PackagingPlanStatus.PLANNED,
                new EnumMap<>(ChecklistItem.class), null, null, null, null, 0);
        Objects.requireNonNull(batchVolumeLiters, "volume do lote é obrigatório");
        if (plan.plannedVolumeLiters.compareTo(batchVolumeLiters) > 0) {
            throw new IllegalArgumentException("volume planejado excede o volume do lote");
        }
        return plan;
    }

    public static PackagingPlan reconstitute(UUID id, UUID breweryId, String code, UUID batchId, UUID containerId,
            BigDecimal containerVolumeMl, int plannedUnits, UUID lineEquipmentId, Instant plannedStart,
            Instant plannedEnd, PackagingPlanStatus status, Map<ChecklistItem, Confirmation> checklist,
            Instant reservedAt, UUID reservedBy, String cancelReason, Instant cancelledAt, long version) {
        return new PackagingPlan(id, breweryId, code, batchId, containerId, containerVolumeMl, plannedUnits,
                lineEquipmentId, plannedStart, plannedEnd, status, checklist, reservedAt, reservedBy, cancelReason,
                cancelledAt, version);
    }

    /**
     * Confirma um item do checklist. Repetir é inócuo: a primeira confirmação é preservada,
     * porque ela é a evidência de quem conferiu e quando.
     */
    public void confirm(ChecklistItem item, UUID actorId, Instant at) {
        requireOpen();
        Objects.requireNonNull(item, "item é obrigatório");
        checklist.putIfAbsent(item, new Confirmation(
                Objects.requireNonNull(actorId, "responsável é obrigatório"),
                Objects.requireNonNull(at, "instante da confirmação é obrigatório")));
    }

    /** Itens que ainda faltam confirmar, na ordem do checklist. */
    public Set<ChecklistItem> pendingChecklist() {
        var pending = EnumSet.allOf(ChecklistItem.class);
        pending.removeAll(checklist.keySet());
        return pending;
    }

    /** Bloqueios que o próprio plano conhece, sem consultar outros módulos. */
    public List<PackagingBlockedException.Blocker> ownBlockers() {
        var blockers = new ArrayList<PackagingBlockedException.Blocker>();
        for (var item : pendingChecklist()) {
            blockers.add(new PackagingBlockedException.Blocker(
                    "checklist_pending", "Item do checklist pendente: " + item.label() + "."));
        }
        return blockers;
    }

    /** Compromete o plano: só de {@code PLANNED} e só com o checklist inteiro confirmado. */
    public void reserve(UUID actorId, Instant at) {
        requireOpen();
        if (status == PackagingPlanStatus.RESERVED) {
            throw new IllegalStateException("plano já reservado");
        }
        if (!pendingChecklist().isEmpty()) {
            throw new PackagingBlockedException(ownBlockers());
        }
        this.status = PackagingPlanStatus.RESERVED;
        this.reservedBy = Objects.requireNonNull(actorId, "responsável é obrigatório");
        this.reservedAt = Objects.requireNonNull(at, "instante da reserva é obrigatório");
    }

    /** Cancela o plano (terminal); o motivo é obrigatório porque a reserva devolvida é auditada. */
    public void cancel(String reason, Instant at) {
        requireOpen();
        this.cancelReason = requireText(reason, "motivo do cancelamento", 200);
        this.cancelledAt = Objects.requireNonNull(at, "instante do cancelamento é obrigatório");
        this.status = PackagingPlanStatus.CANCELLED;
    }

    /** Um plano cancelado não ocupa linha nem segura embalagem. */
    public boolean active() {
        return !status.terminal();
    }

    private void requireOpen() {
        if (status.terminal()) {
            throw new IllegalStateException("plano cancelado não aceita alteração");
        }
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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    /** Quem confirmou um item do checklist e quando. */
    public record Confirmation(UUID actorId, Instant at) {
        public Confirmation {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(at, "at");
        }
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public UUID batchId() { return batchId; }
    public UUID containerId() { return containerId; }
    public BigDecimal containerVolumeMl() { return containerVolumeMl; }
    public int plannedUnits() { return plannedUnits; }
    public BigDecimal plannedVolumeLiters() { return plannedVolumeLiters; }
    public UUID lineEquipmentId() { return lineEquipmentId; }
    public Instant plannedStart() { return plannedStart; }
    public Instant plannedEnd() { return plannedEnd; }
    public PackagingPlanStatus status() { return status; }
    public Map<ChecklistItem, Confirmation> checklist() { return Map.copyOf(checklist); }
    public Instant reservedAt() { return reservedAt; }
    public UUID reservedBy() { return reservedBy; }
    public String cancelReason() { return cancelReason; }
    public Instant cancelledAt() { return cancelledAt; }
    public long version() { return version; }
}
