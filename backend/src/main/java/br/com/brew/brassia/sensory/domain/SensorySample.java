package br.com.brew.brassia.sensory.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Amostra da sessão: o que o provador vê (código cego) e o que o sistema guarda (lote).
 *
 * <p>É essa separação que resolve a tensão da história — a amostra precisa ser cega para quem
 * prova e rastreável para quem audita. O lote <strong>nunca</strong> é apagado; ele apenas não é
 * revelado enquanto a sessão está aberta.
 */
public final class SensorySample {

    private final UUID id;
    private final BlindCode blindCode;
    private final UUID batchId;
    private final String note;

    private SensorySample(UUID id, BlindCode blindCode, UUID batchId, String note) {
        this.id = Objects.requireNonNull(id, "id");
        this.blindCode = Objects.requireNonNull(blindCode, "código cego");
        this.batchId = Objects.requireNonNull(batchId, "lote é obrigatório");
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public static SensorySample of(BlindCode blindCode, UUID batchId, String note) {
        return new SensorySample(UUID.randomUUID(), blindCode, batchId, note);
    }

    public static SensorySample reconstitute(UUID id, BlindCode blindCode, UUID batchId, String note) {
        return new SensorySample(id, blindCode, batchId, note);
    }

    public UUID id() {
        return id;
    }

    public BlindCode blindCode() {
        return blindCode;
    }

    public UUID batchId() {
        return batchId;
    }

    public String note() {
        return note;
    }
}
