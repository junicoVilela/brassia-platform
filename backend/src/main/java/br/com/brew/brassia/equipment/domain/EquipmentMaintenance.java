package br.com.brew.brassia.equipment.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Janela de indisponibilidade de um equipamento (EQP-002): manutenção ou
 * calibração, com instrumento associado quando calibração. Enquanto SCHEDULED,
 * torna o equipamento indisponível no intervalo — daí não poder ser reservado.
 */
public final class EquipmentMaintenance {
    private static final int INSTRUMENT_MAX = 160;
    private static final int NOTES_MAX = 500;

    private final MaintenanceId id;
    private final UUID breweryId;
    private final UUID equipmentId;
    private final MaintenanceKind kind;
    private final String instrument;
    private final TimeRange range;
    private final String notes;
    private MaintenanceStatus status;
    private final long version;

    private EquipmentMaintenance(MaintenanceId id, UUID breweryId, UUID equipmentId, MaintenanceKind kind,
            String instrument, TimeRange range, String notes, MaintenanceStatus status, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.equipmentId = Objects.requireNonNull(equipmentId);
        this.kind = Objects.requireNonNull(kind);
        this.range = Objects.requireNonNull(range);
        this.instrument = normalizeInstrument(kind, instrument);
        this.notes = normalizeNotes(notes);
        this.status = Objects.requireNonNull(status);
        this.version = version;
    }

    public static EquipmentMaintenance schedule(UUID breweryId, UUID equipmentId, MaintenanceKind kind,
            String instrument, TimeRange range, String notes) {
        return new EquipmentMaintenance(MaintenanceId.newId(), breweryId, equipmentId, kind, instrument, range,
                notes, MaintenanceStatus.SCHEDULED, 0);
    }

    public static EquipmentMaintenance reconstitute(MaintenanceId id, UUID breweryId, UUID equipmentId,
            MaintenanceKind kind, String instrument, TimeRange range, String notes, MaintenanceStatus status,
            long version) {
        return new EquipmentMaintenance(id, breweryId, equipmentId, kind, instrument, range, notes, status, version);
    }

    /** Cancela a janela agendada, liberando o equipamento. */
    public void cancel() {
        if (status != MaintenanceStatus.SCHEDULED) {
            throw new IllegalStateException("janela não está agendada");
        }
        this.status = MaintenanceStatus.CANCELLED;
    }

    private static String normalizeInstrument(MaintenanceKind kind, String instrument) {
        var value = instrument == null ? null : instrument.trim();
        if (kind == MaintenanceKind.CALIBRATION && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("calibração exige instrumento associado");
        }
        if (value != null && value.length() > INSTRUMENT_MAX) {
            throw new IllegalArgumentException("instrumento muito longo");
        }
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        var value = notes.trim();
        if (value.length() > NOTES_MAX) {
            throw new IllegalArgumentException("observação muito longa");
        }
        return value;
    }

    public MaintenanceId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID equipmentId() { return equipmentId; }
    public MaintenanceKind kind() { return kind; }
    public String instrument() { return instrument; }
    public TimeRange range() { return range; }
    public String notes() { return notes; }
    public MaintenanceStatus status() { return status; }
    public long version() { return version; }
}
