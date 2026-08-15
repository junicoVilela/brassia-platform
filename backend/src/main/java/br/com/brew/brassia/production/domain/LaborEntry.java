package br.com.brew.brassia.production.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Horas trabalhadas num lote (CST-001-A).
 *
 * <p><strong>Registra hora, não dinheiro.</strong> Quanto vale a hora é decisão de gestão e mora no
 * custeio: separar permite ajustar a taxa sem reescrever apontamento, e evita que quem aponta seis horas
 * de brassa precise conhecer moeda para fazê-lo.
 */
public final class LaborEntry {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final String activity;
    private final Instant startedAt;
    private final Instant endedAt;
    private final int people;
    private final UUID recordedBy;
    private final Instant recordedAt;

    private LaborEntry(UUID id, UUID breweryId, UUID batchId, String activity, Instant startedAt,
            Instant endedAt, int people, UUID recordedBy, Instant recordedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria é obrigatória");
        this.batchId = Objects.requireNonNull(batchId, "lote é obrigatório");
        this.activity = requireText(activity);
        this.startedAt = Objects.requireNonNull(startedAt, "início é obrigatório");
        this.endedAt = Objects.requireNonNull(endedAt, "fim é obrigatório");
        if (!endedAt.isAfter(startedAt)) {
            // Período de duração zero ou negativa não é trabalho: seria custo de mão de obra saindo de um
            // apontamento que descreve ninguém trabalhando.
            throw new IllegalArgumentException("o fim precisa ser depois do início");
        }
        if (people < 1) {
            throw new IllegalArgumentException("apontamento precisa de ao menos uma pessoa");
        }
        this.people = people;
        this.recordedBy = Objects.requireNonNull(recordedBy, "autor do apontamento é obrigatório");
        this.recordedAt = Objects.requireNonNull(recordedAt, "instante do apontamento é obrigatório");
    }

    public static LaborEntry record(UUID breweryId, UUID batchId, String activity, Instant startedAt,
            Instant endedAt, int people, UUID actorId, Instant at) {
        return new LaborEntry(UUID.randomUUID(), breweryId, batchId, activity, startedAt, endedAt, people,
                actorId, at);
    }

    public static LaborEntry reconstitute(UUID id, UUID breweryId, UUID batchId, String activity,
            Instant startedAt, Instant endedAt, int people, UUID recordedBy, Instant recordedAt) {
        return new LaborEntry(id, breweryId, batchId, activity, startedAt, endedAt, people, recordedBy,
                recordedAt);
    }

    /**
     * Horas-homem: a duração multiplicada pelas pessoas.
     *
     * <p>Duas pessoas por três horas custam seis horas-homem. Guardar "3 h" perderia exatamente a metade
     * que a cervejaria paga.
     */
    public BigDecimal manHours() {
        var minutes = BigDecimal.valueOf(Duration.between(startedAt, endedAt).toMinutes());
        return minutes.multiply(BigDecimal.valueOf(people))
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a atividade é obrigatória");
        }
        var trimmed = value.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("atividade excede 120 caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public String activity() { return activity; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public int people() { return people; }
    public UUID recordedBy() { return recordedBy; }
    public Instant recordedAt() { return recordedAt; }
}
