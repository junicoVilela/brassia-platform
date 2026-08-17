package br.com.brew.brassia.distribution.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O que aconteceu numa parada: a prova de entrega e a coleta (LOG-002).
 *
 * <p><strong>Isto não se edita.</strong> É o critério transversal da sprint — todo movimento é
 * append-only e corrige por evento compensatório. Uma prova de entrega reescrita é a pior espécie de
 * registro: ela parece original e diz outra coisa, e ninguém consegue saber o que o entregador anotou às
 * dez da manhã. Errou? Registra-se uma correção que aponta para esta, e as duas ficam.
 *
 * <p><strong>Entregar e coletar são fatos separados</strong>, e não dois lados da mesma moeda: o motorista
 * frequentemente recolhe vasilhames vazios num bar onde não deixou nada, e às vezes deixa sem recolher.
 * Amarrá-los faria uma coleta exigir uma entrega inventada.
 */
public final class ProofOfDelivery {

    private final UUID id;
    private final UUID stopId;
    private final DeliveryOutcome outcome;
    private final Instant occurredAt;
    private final UUID recordedBy;
    private final List<UUID> deliveredContainerIds;
    private final List<UUID> collectedContainerIds;
    private final String note;
    private final ConsentedMedia media;
    private final CoarseLocation location;
    private final boolean outsideWindow;
    /** Quando esta prova corrige outra: a original continua de pé, e esta explica o que mudou. */
    private final UUID correctsProofId;

    private ProofOfDelivery(UUID id, UUID stopId, DeliveryOutcome outcome, Instant occurredAt,
            UUID recordedBy, List<UUID> deliveredContainerIds, List<UUID> collectedContainerIds,
            String note, ConsentedMedia media, CoarseLocation location, boolean outsideWindow,
            UUID correctsProofId) {
        this.id = Objects.requireNonNull(id);
        this.stopId = Objects.requireNonNull(stopId, "parada");
        this.outcome = Objects.requireNonNull(outcome, "desfecho");
        this.occurredAt = Objects.requireNonNull(occurredAt, "quando aconteceu");
        this.recordedBy = Objects.requireNonNull(recordedBy, "quem registrou");
        this.deliveredContainerIds = List.copyOf(deliveredContainerIds);
        this.collectedContainerIds = List.copyOf(collectedContainerIds);
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.media = media;
        this.location = location;
        this.outsideWindow = outsideWindow;
        this.correctsProofId = correctsProofId;
        validate();
    }

    public static ProofOfDelivery record(UUID id, UUID stopId, DeliveryOutcome outcome,
            Instant occurredAt, UUID recordedBy, List<UUID> delivered, List<UUID> collected,
            String note, ConsentedMedia media, CoarseLocation location, boolean outsideWindow) {
        return new ProofOfDelivery(id, stopId, outcome, occurredAt, recordedBy, delivered, collected,
                note, media, location, outsideWindow, null);
    }

    /**
     * A correção: um registro novo que aponta para o errado.
     *
     * <p>Ela não apaga nem reescreve. O que aconteceu de fato passa a ser a última palavra, e o caminho
     * até ela continua legível — que é o que separa uma correção de um encobrimento.
     */
    public static ProofOfDelivery correcting(UUID id, ProofOfDelivery original,
            DeliveryOutcome outcome, Instant occurredAt, UUID recordedBy, List<UUID> delivered,
            List<UUID> collected, String reason) {
        if (reason == null || reason.isBlank()) {
            // Uma correção sem motivo é uma versão nova sem explicação, e quem lê seis meses depois não
            // sabe qual das duas acreditar.
            throw new IllegalArgumentException("a correção precisa dizer o que estava errado");
        }
        if (original.correctsProofId != null) {
            // Corrigir a correção encadearia versões e tornaria "a última palavra" uma pergunta.
            throw new IllegalArgumentException("corrija a prova original, e não a correção");
        }
        return new ProofOfDelivery(id, original.stopId, outcome, occurredAt, recordedBy, delivered,
                collected, reason, null, null, original.outsideWindow, original.id);
    }

    /** Volta do banco como correção: reconstituí-la como original perderia o elo que a torna auditável. */
    public static ProofOfDelivery reconstituteCorrection(UUID id, UUID stopId, UUID correctsProofId,
            DeliveryOutcome outcome, Instant occurredAt, UUID recordedBy, List<UUID> delivered,
            List<UUID> collected, String note, boolean outsideWindow) {
        return new ProofOfDelivery(id, stopId, outcome, occurredAt, recordedBy, delivered, collected,
                note, null, null, outsideWindow, correctsProofId);
    }

    private void validate() {
        if (outcome == DeliveryOutcome.DELIVERED && deliveredContainerIds.isEmpty()) {
            // "Entregue" sem nada entregue é o clique automático do fim do dia, e ele fecharia a parada
            // com o caminhão ainda cheio.
            throw new IllegalArgumentException("uma entrega sem itens não é uma entrega");
        }
        if (outcome == DeliveryOutcome.PARTIAL && deliveredContainerIds.isEmpty()) {
            throw new IllegalArgumentException("entrega parcial precisa dizer o que desceu");
        }
        if (naoEntregou() && !deliveredContainerIds.isEmpty()) {
            // Recusado com itens entregues é contradição, e o estoque acreditaria em uma das duas metades.
            throw new IllegalArgumentException(
                    "não se registra item entregue num desfecho de não entrega");
        }
        if (naoEntregou() && note == null) {
            // O que fazer amanhã depende do motivo, e "recusado" sozinho não diz se foi preço, avaria ou
            // pedido errado.
            throw new IllegalArgumentException("uma não entrega precisa do motivo");
        }
        if (deliveredContainerIds.stream().anyMatch(collectedContainerIds::contains)) {
            throw new IllegalArgumentException(
                    "o mesmo vasilhame não é entregue e recolhido na mesma parada");
        }
    }

    private boolean naoEntregou() {
        return outcome == DeliveryOutcome.REFUSED || outcome == DeliveryOutcome.ABSENT
                || outcome == DeliveryOutcome.RESCHEDULED;
    }

    /** O que não desceu volta para casa — e a carga precisa saber disso hoje, e não amanhã. */
    public List<UUID> returning(List<UUID> plannedContainerIds) {
        return plannedContainerIds.stream().filter(c -> !deliveredContainerIds.contains(c)).toList();
    }

    public boolean isCorrection() {
        return correctsProofId != null;
    }

    public boolean hasMedia() {
        return media != null;
    }

    public UUID id() {
        return id;
    }

    public UUID stopId() {
        return stopId;
    }

    public DeliveryOutcome outcome() {
        return outcome;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID recordedBy() {
        return recordedBy;
    }

    public List<UUID> deliveredContainerIds() {
        return deliveredContainerIds;
    }

    public List<UUID> collectedContainerIds() {
        return collectedContainerIds;
    }

    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    public Optional<ConsentedMedia> media() {
        return Optional.ofNullable(media);
    }

    public Optional<CoarseLocation> location() {
        return Optional.ofNullable(location);
    }

    /** A janela era compromisso: quando ela é perdida, o registro guarda isso sem impedir a entrega. */
    public boolean outsideWindow() {
        return outsideWindow;
    }

    public Optional<UUID> correctsProofId() {
        return Optional.ofNullable(correctsProofId);
    }
}
