package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Controle de frescor do envase (FSL-001): a evidência de oxigênio e a validade que saiu dela.
 *
 * <p>A recomendação e o override convivem: o recomendado nunca é sobrescrito. Guardar os dois lado
 * a lado é o que permite, meses depois, saber se a validade impressa foi a que a evidência sugeria
 * ou uma decisão humana — e, no segundo caso, por quê. Sobrepor exige motivo e é auditado.
 *
 * <p>Sem política de vida útil configurada não há recomendação: a medição continua sendo gravada
 * (evidência não se descarta) e a validade passa a ser decisão humana registrada.
 */
public final class FreshnessRecord {

    private final UUID planId;
    private final UUID breweryId;
    private final LocalDate packagedOn;
    private final OxygenMeasurement measurement;
    private final Integer recommendedShelfLifeDays;
    private final LocalDate recommendedBestBefore;
    private final UUID recordedBy;
    private final Instant recordedAt;
    private Integer overrideShelfLifeDays;
    private LocalDate overrideBestBefore;
    private String overrideReason;
    private UUID overriddenBy;
    private Instant overriddenAt;
    private final long version;

    private FreshnessRecord(UUID planId, UUID breweryId, LocalDate packagedOn, OxygenMeasurement measurement,
            Integer recommendedShelfLifeDays, LocalDate recommendedBestBefore, UUID recordedBy, Instant recordedAt,
            Integer overrideShelfLifeDays, LocalDate overrideBestBefore, String overrideReason, UUID overriddenBy,
            Instant overriddenAt, long version) {
        this.planId = Objects.requireNonNull(planId, "plano é obrigatório");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.packagedOn = Objects.requireNonNull(packagedOn, "data do envase é obrigatória");
        this.measurement = Objects.requireNonNull(measurement, "medição é obrigatória");
        if ((recommendedShelfLifeDays == null) != (recommendedBestBefore == null)) {
            throw new IllegalArgumentException("recomendação precisa de dias e data juntos");
        }
        this.recommendedShelfLifeDays = recommendedShelfLifeDays;
        this.recommendedBestBefore = recommendedBestBefore;
        this.recordedBy = Objects.requireNonNull(recordedBy, "responsável é obrigatório");
        this.recordedAt = Objects.requireNonNull(recordedAt, "instante do registro é obrigatório");
        this.overrideShelfLifeDays = overrideShelfLifeDays;
        this.overrideBestBefore = overrideBestBefore;
        this.overrideReason = overrideReason;
        this.overriddenBy = overriddenBy;
        this.overriddenAt = overriddenAt;
        this.version = version;
    }

    /**
     * Registra a evidência e a recomendação derivada dela. {@code recommendation} nulo significa
     * cervejaria sem política de vida útil: a medição vale, a validade fica a decidir.
     */
    public static FreshnessRecord record(UUID planId, UUID breweryId, LocalDate packagedOn,
            OxygenMeasurement measurement, ShelfLifeRecommendation recommendation, UUID actorId, Instant at) {
        return new FreshnessRecord(planId, breweryId, packagedOn, measurement,
                recommendation == null ? null : recommendation.shelfLifeDays(),
                recommendation == null ? null : recommendation.bestBefore(),
                actorId, at, null, null, null, null, null, 0);
    }

    public static FreshnessRecord reconstitute(UUID planId, UUID breweryId, LocalDate packagedOn,
            OxygenMeasurement measurement, Integer recommendedShelfLifeDays, LocalDate recommendedBestBefore,
            UUID recordedBy, Instant recordedAt, Integer overrideShelfLifeDays, LocalDate overrideBestBefore,
            String overrideReason, UUID overriddenBy, Instant overriddenAt, long version) {
        return new FreshnessRecord(planId, breweryId, packagedOn, measurement, recommendedShelfLifeDays,
                recommendedBestBefore, recordedBy, recordedAt, overrideShelfLifeDays, overrideBestBefore,
                overrideReason, overriddenBy, overriddenAt, version);
    }

    /**
     * Sobrepõe a validade recomendada. O motivo é obrigatório porque é ele que explica, depois, uma
     * data que a evidência não sustentava. A validade sobreposta não pode ser anterior ao envase.
     */
    public void override(int shelfLifeDays, String reason, UUID actorId, Instant at) {
        if (shelfLifeDays < 1) {
            throw new IllegalArgumentException("validade sobreposta deve ser positiva (dias)");
        }
        this.overrideReason = requireText(reason, "motivo do override", 200);
        this.overrideShelfLifeDays = shelfLifeDays;
        this.overrideBestBefore = packagedOn.plusDays(shelfLifeDays);
        this.overriddenBy = Objects.requireNonNull(actorId, "responsável pelo override é obrigatório");
        this.overriddenAt = Objects.requireNonNull(at, "instante do override é obrigatório");
    }

    /** A validade que vale: a sobreposta quando existe, senão a recomendada. */
    public LocalDate effectiveBestBefore() {
        return overrideBestBefore != null ? overrideBestBefore : recommendedBestBefore;
    }

    public Integer effectiveShelfLifeDays() {
        return overrideShelfLifeDays != null ? overrideShelfLifeDays : recommendedShelfLifeDays;
    }

    public boolean overridden() {
        return overrideBestBefore != null;
    }

    /** Override que estende a validade além do que a evidência sustentava. */
    public boolean extendsBeyondRecommendation() {
        return overridden() && recommendedShelfLifeDays != null
                && overrideShelfLifeDays > recommendedShelfLifeDays;
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

    public UUID planId() { return planId; }
    public UUID breweryId() { return breweryId; }
    public LocalDate packagedOn() { return packagedOn; }
    public OxygenMeasurement measurement() { return measurement; }
    public Integer recommendedShelfLifeDays() { return recommendedShelfLifeDays; }
    public LocalDate recommendedBestBefore() { return recommendedBestBefore; }
    public UUID recordedBy() { return recordedBy; }
    public Instant recordedAt() { return recordedAt; }
    public Integer overrideShelfLifeDays() { return overrideShelfLifeDays; }
    public LocalDate overrideBestBefore() { return overrideBestBefore; }
    public String overrideReason() { return overrideReason; }
    public UUID overriddenBy() { return overriddenBy; }
    public Instant overriddenAt() { return overriddenAt; }
    public long version() { return version; }
}
