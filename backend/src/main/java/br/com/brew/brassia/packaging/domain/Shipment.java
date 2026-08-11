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
 * <p><strong>Não se corrige: estorna-se (FDS-003-A).</strong> Sobrescrever a saída apagaria o destino que
 * já foi comunicado num recall. O estorno mantém a linha, marca que ela não vale mais e exige o porquê —
 * assim "nunca houve expedição" continua distinguível de "houve e foi estornada", e a segunda é
 * demonstrável para quem recebeu a comunicação baseada nela.
 *
 * <p>Devolução e transferência entre destinos continuam fora: são movimentação comercial, dependem de
 * cliente e pedido, e são assunto das sprints 19 e 20.
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
    private Reversal reversal;

    private Shipment(UUID id, UUID breweryId, UUID finishedLotId, String destination, String contact,
            int units, LocalDate shippedOn, String note, UUID recordedBy, Instant recordedAt,
            Reversal reversal) {
        this.reversal = reversal;
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
                shippedOn, note, actorId, at, null);
    }

    public static Shipment reconstitute(UUID id, UUID breweryId, UUID finishedLotId, String destination,
            String contact, int units, LocalDate shippedOn, String note, UUID recordedBy, Instant recordedAt,
            Reversal reversal) {
        return new Shipment(id, breweryId, finishedLotId, destination, contact, units, shippedOn, note,
                recordedBy, recordedAt, reversal);
    }

    /**
     * Estorna a expedição registrada errada.
     *
     * <p>A justificativa é obrigatória e não aceita evasiva. Sem ela, o histórico mostraria uma expedição
     * que deixou de valer sem dizer se foi erro de digitação, destino trocado ou carga que não saiu — e as
     * três exigem reações diferentes de quem investiga.
     *
     * <p>Estornar duas vezes é recusado: a segunda tentativa é uma repetição, e aceitá-la sobrescreveria o
     * autor e a data do estorno que realmente aconteceu.
     */
    public void reverse(UUID actorId, String reason, Instant at) {
        if (reversal != null) {
            throw new AlreadyReversedException(id, reversal.at());
        }
        reversal = new Reversal(Objects.requireNonNull(actorId, "autor do estorno é obrigatório"),
                requireReason(reason), Objects.requireNonNull(at, "instante do estorno é obrigatório"));
    }

    /** Vale para o recall? Estornada não conta como saída, e é isso que a torna diferente de apagada. */
    public boolean isActive() {
        return reversal == null;
    }

    public java.util.Optional<Reversal> reversal() {
        return java.util.Optional.ofNullable(reversal);
    }

    private static String requireReason(String reason) {
        var text = reason == null ? "" : reason.trim();
        if (text.length() < 5) {
            // Curto demais é "n/a" com outro nome: a justificativa existe para ser lida meses depois.
            throw new IllegalArgumentException("o motivo do estorno precisa dizer o que houve");
        }
        if (text.length() > MAX_NOTE) {
            throw new IllegalArgumentException("motivo excede " + MAX_NOTE + " caracteres");
        }
        return text;
    }

    /** Quem estornou, por quê e quando. Os três juntos ou nenhum — meio estorno não existe. */
    public record Reversal(UUID by, String reason, Instant at) {

        public Reversal {
            Objects.requireNonNull(by, "autor do estorno");
            Objects.requireNonNull(reason, "motivo do estorno");
            Objects.requireNonNull(at, "instante do estorno");
        }
    }

    /** Segunda tentativa de estornar a mesma expedição. */
    public static final class AlreadyReversedException extends RuntimeException {

        private final UUID shipmentId;
        private final Instant reversedAt;

        AlreadyReversedException(UUID shipmentId, Instant reversedAt) {
            super("expedição já estornada em " + reversedAt);
            this.shipmentId = shipmentId;
            this.reversedAt = reversedAt;
        }

        public UUID shipmentId() {
            return shipmentId;
        }

        public Instant reversedAt() {
            return reversedAt;
        }
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
