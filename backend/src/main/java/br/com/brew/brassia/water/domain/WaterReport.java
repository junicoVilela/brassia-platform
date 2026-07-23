package br.com.brew.brassia.water.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Laudo de água de uma fonte (WTR-001): composição iônica, data da coleta e
 * método. É <em>imutável</em> — uma correção gera um novo laudo, e o laudo antigo
 * permanece disponível no histórico.
 */
public final class WaterReport {
    private static final int NOTES_MAX = 500;

    private final WaterReportId id;
    private final UUID breweryId;
    private final WaterSourceId sourceId;
    private final LocalDate collectedOn;
    private final WaterMethod method;
    private final IonProfile ions;
    private final String notes;

    private WaterReport(WaterReportId id, UUID breweryId, WaterSourceId sourceId, LocalDate collectedOn,
            WaterMethod method, IonProfile ions, String notes) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.sourceId = Objects.requireNonNull(sourceId);
        this.collectedOn = requireCollectedOn(collectedOn);
        this.method = Objects.requireNonNull(method);
        this.ions = Objects.requireNonNull(ions);
        this.notes = normalizeNotes(notes);
    }

    public static WaterReport record(UUID breweryId, WaterSourceId sourceId, LocalDate collectedOn,
            WaterMethod method, IonProfile ions, String notes) {
        return new WaterReport(WaterReportId.newId(), breweryId, sourceId, collectedOn, method, ions, notes);
    }

    public static WaterReport reconstitute(WaterReportId id, UUID breweryId, WaterSourceId sourceId,
            LocalDate collectedOn, WaterMethod method, IonProfile ions, String notes) {
        return new WaterReport(id, breweryId, sourceId, collectedOn, method, ions, notes);
    }

    private static LocalDate requireCollectedOn(LocalDate collectedOn) {
        if (collectedOn == null) {
            throw new IllegalArgumentException("data da coleta obrigatória");
        }
        if (collectedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("data da coleta não pode ser futura");
        }
        return collectedOn;
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

    public WaterReportId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public WaterSourceId sourceId() { return sourceId; }
    public LocalDate collectedOn() { return collectedOn; }
    public WaterMethod method() { return method; }
    public IonProfile ions() { return ions; }
    public String notes() { return notes; }
}
