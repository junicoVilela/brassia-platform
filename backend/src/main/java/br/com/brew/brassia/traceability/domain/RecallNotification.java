package br.com.brew.brassia.traceability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um destino alcançado pelo recall, e o que se fez a respeito (FDS-003).
 *
 * <p><strong>É a parte guardada, e tem de ser.</strong> Notificar um cliente é um fato sobre o que
 * a cervejaria fez — derivá-lo do grafo apagaria a prova de que ele foi avisado. Por isso os dados
 * do destino são copiados da expedição no momento da abertura: o dossiê precisa continuar dizendo
 * para quem se ligou e em que número, mesmo que o cadastro mude depois.
 *
 * <p>Nasce pendente. Um recall cujo dossiê nasce "tudo comunicado" seria um dossiê que mente.
 */
public final class RecallNotification {

    private static final int MAX_NOTE = 500;

    private final UUID id;
    private final UUID recallId;
    private final UUID shipmentId;
    private final String finishedLotCode;
    private final String destination;
    private final String contact;
    private final int units;
    private NotificationStatus status;
    private String channel;
    private String note;
    private UUID notifiedBy;
    private Instant notifiedAt;

    private RecallNotification(UUID id, UUID recallId, UUID shipmentId, String finishedLotCode,
            String destination, String contact, int units, NotificationStatus status, String channel,
            String note, UUID notifiedBy, Instant notifiedAt) {
        this.id = Objects.requireNonNull(id);
        this.recallId = Objects.requireNonNull(recallId);
        this.shipmentId = Objects.requireNonNull(shipmentId, "expedição é obrigatória");
        this.finishedLotCode = Objects.requireNonNull(finishedLotCode, "lote é obrigatório");
        this.destination = Objects.requireNonNull(destination, "destino é obrigatório");
        this.contact = contact;
        this.units = units;
        this.status = Objects.requireNonNull(status);
        this.channel = channel;
        this.note = note;
        this.notifiedBy = notifiedBy;
        this.notifiedAt = notifiedAt;
    }

    public static RecallNotification pending(UUID recallId, UUID shipmentId, String finishedLotCode,
            String destination, String contact, int units) {
        return new RecallNotification(UUID.randomUUID(), recallId, shipmentId, finishedLotCode, destination,
                contact, units, NotificationStatus.PENDING, null, null, null, null);
    }

    public static RecallNotification reconstitute(UUID id, UUID recallId, UUID shipmentId,
            String finishedLotCode, String destination, String contact, int units,
            NotificationStatus status, String channel, String note, UUID notifiedBy, Instant notifiedAt) {
        return new RecallNotification(id, recallId, shipmentId, finishedLotCode, destination, contact, units,
                status, channel, note, notifiedBy, notifiedAt);
    }

    /**
     * Registra que o destino foi comunicado.
     *
     * <p>O canal é obrigatório porque "avisamos" sem dizer como não é prova de nada; e a mesma
     * comunicação não se registra duas vezes, senão o dossiê passa a contar avisos que não houve.
     */
    public void notified(UUID actorId, String channel, String note, Instant at) {
        if (status == NotificationStatus.NOTIFIED) {
            throw new IllegalStateException("este destino já foi comunicado");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("registrar comunicação exige o canal usado");
        }
        if (note != null && note.trim().length() > MAX_NOTE) {
            throw new IllegalArgumentException("observação excede " + MAX_NOTE + " caracteres");
        }
        this.status = NotificationStatus.NOTIFIED;
        this.channel = channel.trim();
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.notifiedBy = Objects.requireNonNull(actorId, "autor da comunicação é obrigatório");
        this.notifiedAt = Objects.requireNonNull(at, "instante da comunicação é obrigatório");
    }

    public boolean pending() {
        return status == NotificationStatus.PENDING;
    }

    public UUID id() { return id; }
    public UUID recallId() { return recallId; }
    public UUID shipmentId() { return shipmentId; }
    public String finishedLotCode() { return finishedLotCode; }
    public String destination() { return destination; }
    public String contact() { return contact; }
    public int units() { return units; }
    public NotificationStatus status() { return status; }
    public String channel() { return channel; }
    public String note() { return note; }
    public UUID notifiedBy() { return notifiedBy; }
    public Instant notifiedAt() { return notifiedAt; }

    public enum NotificationStatus {
        PENDING,
        NOTIFIED
    }
}
