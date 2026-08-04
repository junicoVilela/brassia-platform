package br.com.brew.brassia.sensory.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ficha de um provador para uma amostra (SEN-001).
 *
 * <p><strong>Imutável depois de enviada.</strong> Sem isso bastaria esperar o fechamento, ver o
 * resultado e reescrever a própria ficha — o que transformaria a sessão num exercício de
 * concordância retroativa.
 */
public final class SensoryEvaluation {

    private final UUID id;
    private final UUID breweryId;
    private final UUID sessionId;
    private final UUID sampleId;
    private final UUID tasterId;
    private final Map<SensoryAttribute, Integer> scores;
    private final List<String> descriptors;
    private final String note;
    private final Instant submittedAt;

    private SensoryEvaluation(UUID id, UUID breweryId, UUID sessionId, UUID sampleId, UUID tasterId,
            Map<SensoryAttribute, Integer> scores, List<String> descriptors, String note,
            Instant submittedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.sampleId = Objects.requireNonNull(sampleId, "sampleId");
        this.tasterId = Objects.requireNonNull(tasterId, "provador");
        this.scores = requireScores(scores);
        this.descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descritores"));
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.submittedAt = Objects.requireNonNull(submittedAt, "instante do envio");
    }

    public static SensoryEvaluation submit(UUID breweryId, UUID sessionId, UUID sampleId, UUID tasterId,
            Map<SensoryAttribute, Integer> scores, List<String> descriptors, String note,
            Instant submittedAt) {
        return new SensoryEvaluation(UUID.randomUUID(), breweryId, sessionId, sampleId, tasterId, scores,
                descriptors, note, submittedAt);
    }

    public static SensoryEvaluation reconstitute(UUID id, UUID breweryId, UUID sessionId, UUID sampleId,
            UUID tasterId, Map<SensoryAttribute, Integer> scores, List<String> descriptors, String note,
            Instant submittedAt) {
        return new SensoryEvaluation(id, breweryId, sessionId, sampleId, tasterId, scores, descriptors, note,
                submittedAt);
    }

    public int score(SensoryAttribute attribute) {
        return scores.get(attribute);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID sampleId() {
        return sampleId;
    }

    public UUID tasterId() {
        return tasterId;
    }

    public Map<SensoryAttribute, Integer> scores() {
        return Map.copyOf(scores);
    }

    public List<String> descriptors() {
        return descriptors;
    }

    public String note() {
        return note;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    /** Ficha incompleta não é ficha: faltando um atributo, a média do painel compararia coisas diferentes. */
    private static Map<SensoryAttribute, Integer> requireScores(Map<SensoryAttribute, Integer> scores) {
        Objects.requireNonNull(scores, "notas");
        var copy = new EnumMap<SensoryAttribute, Integer>(SensoryAttribute.class);
        for (var attribute : SensoryAttribute.values()) {
            var score = scores.get(attribute);
            if (score == null) {
                throw new IllegalArgumentException("falta a nota de " + attribute.label());
            }
            if (score < SensoryAttribute.MIN_SCORE || score > SensoryAttribute.MAX_SCORE) {
                throw new IllegalArgumentException("a nota de %s deve ficar entre %d e %d"
                        .formatted(attribute.label(), SensoryAttribute.MIN_SCORE,
                                SensoryAttribute.MAX_SCORE));
            }
            copy.put(attribute, score);
        }
        return copy;
    }
}
