package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada da agenda de produção (PLN-001): a intenção de brassar uma receita
 * publicada, em um equipamento, numa janela de tempo, sob um responsável.
 *
 * <p>Invariantes: volume planejado positivo e não superior à capacidade do
 * equipamento referenciado (consulta publicada); janela válida (ver
 * {@link ScheduleWindow}). O conflito de equipamento (sobreposição de janelas no
 * mesmo equipamento) é verificado no caso de uso contra as entradas existentes,
 * pois depende de estado externo ao agregado.
 */
public final class ScheduleEntry {

    private final ScheduleEntryId id;
    private final UUID breweryId;
    private final UUID recipeId;
    private final UUID equipmentId;
    private final UUID assignedUserId;
    private final BigDecimal plannedVolumeLiters;
    private final ScheduleWindow window;
    private final ScheduleStatus status;
    private final long version;

    private ScheduleEntry(ScheduleEntryId id, UUID breweryId, UUID recipeId, UUID equipmentId,
            UUID assignedUserId, BigDecimal plannedVolumeLiters, ScheduleWindow window,
            ScheduleStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId");
        this.assignedUserId = Objects.requireNonNull(assignedUserId, "assignedUserId");
        this.plannedVolumeLiters = requirePositive(plannedVolumeLiters);
        this.window = Objects.requireNonNull(window, "window");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    /**
     * Planeja uma nova entrada da agenda.
     *
     * @param capacityLiters capacidade do equipamento referenciado (consulta publicada)
     */
    public static ScheduleEntry plan(UUID breweryId, UUID recipeId, UUID equipmentId, UUID assignedUserId,
            BigDecimal plannedVolumeLiters, BigDecimal capacityLiters, ScheduleWindow window) {
        if (capacityLiters != null && plannedVolumeLiters != null
                && plannedVolumeLiters.compareTo(capacityLiters) > 0) {
            throw new IllegalArgumentException("volume planejado excede a capacidade do equipamento");
        }
        return new ScheduleEntry(ScheduleEntryId.newId(), breweryId, recipeId, equipmentId, assignedUserId,
                plannedVolumeLiters, window, ScheduleStatus.PLANNED, 1);
    }

    public static ScheduleEntry reconstitute(ScheduleEntryId id, UUID breweryId, UUID recipeId, UUID equipmentId,
            UUID assignedUserId, BigDecimal plannedVolumeLiters, ScheduleWindow window, ScheduleStatus status,
            long version) {
        return new ScheduleEntry(id, breweryId, recipeId, equipmentId, assignedUserId, plannedVolumeLiters,
                window, status, version);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        Objects.requireNonNull(value, "plannedVolumeLiters");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("volume planejado deve ser positivo");
        }
        return value;
    }

    public ScheduleEntryId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }

    public UUID assignedUserId() {
        return assignedUserId;
    }

    public BigDecimal plannedVolumeLiters() {
        return plannedVolumeLiters;
    }

    public ScheduleWindow window() {
        return window;
    }

    public ScheduleStatus status() {
        return status;
    }

    public long version() {
        return version;
    }
}
