package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Expedição de um lote de produto acabado (TRC-001-D): para onde a cerveja foi.
 *
 * <p>É a metade de fora da fábrica, que faltava desde a TRC-001. Sem ela um recall identifica a
 * origem e não alcança ninguém — e "a quem avisar" é metade da FDS-003.
 *
 * <p><strong>Fatia mínima, de propósito.</strong> Não há pedido, nota nem cliente cadastrado: o
 * destino é texto de quem expediu, porque distribuição comercial é assunto das sprints 19 e 20, e
 * criar um cadastro de clientes por aqui seria decidir por elas.
 *
 * <p>Não há comando de correção: expedição é fato registrado. Corrigir uma saída errada é assunto
 * de outra história, e sobrescrevê-la em silêncio apagaria o destino que já foi comunicado num
 * recall.
 */
public final class Shipment {

    private static final int MAX_DESTINATION = 200;
    private static final int MAX_NOTE = 500;

    private final UUID id;
    private final UUID breweryId;
    private final UUID finishedLotId;
    private final String destination;
    private final String contact;
    private final int units;
    private final LocalDate shippedOn;
    private final String note;
    private final UUID recordedBy;
    private final Instant recordedAt;

    private Shipment(UUID id, UUID breweryId, UUID finishedLotId, String destination, String contact,
            int units, LocalDate shippedOn, String note, UUID recordedBy, Instant recordedAt) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria é obrigatória");
        this.finishedLotId = Objects.requireNonNull(finishedLotId, "lote de produto acabado é obrigatório");
        this.destination = requireText(destination, "destino", MAX_DESTINATION);
        this.contact = trimToNull(contact, MAX_DESTINATION, "contato");
        if (units <= 0) {
            throw new IllegalArgumentException("expedição sem unidades não é expedição");
        }
        this.units = units;
        this.shippedOn = Objects.requireNonNull(shippedOn, "data da expedição é obrigatória");
        this.note = trimToNull(note, MAX_NOTE, "observação");
        this.recordedBy = Objects.requireNonNull(recordedBy, "autor do registro é obrigatório");
        this.recordedAt = Objects.requireNonNull(recordedAt, "instante do registro é obrigatório");
    }

    public static Shipment record(UUID breweryId, UUID finishedLotId, String destination, String contact,
            int units, LocalDate shippedOn, String note, UUID actorId, Instant at) {
        return new Shipment(UUID.randomUUID(), breweryId, finishedLotId, destination, contact, units,
                shippedOn, note, actorId, at);
    }

    public static Shipment reconstitute(UUID id, UUID breweryId, UUID finishedLotId, String destination,
            String contact, int units, LocalDate shippedOn, String note, UUID recordedBy, Instant recordedAt) {
        return new Shipment(id, breweryId, finishedLotId, destination, contact, units, shippedOn, note,
                recordedBy, recordedAt);
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

    private static String trimToNull(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID finishedLotId() { return finishedLotId; }
    public String destination() { return destination; }
    public String contact() { return contact; }
    public int units() { return units; }
    public LocalDate shippedOn() { return shippedOn; }
    public String note() { return note; }
    public UUID recordedBy() { return recordedBy; }
    public Instant recordedAt() { return recordedAt; }
}
