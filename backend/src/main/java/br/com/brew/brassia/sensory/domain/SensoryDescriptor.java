package br.com.brew.brassia.sensory.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Um descritor sensorial com vocabulário, fonte e hipóteses de causa (SEN-002).
 *
 * <p><strong>Sinônimos existem porque o vocabulário é regional e a série histórica não é.</strong> Uma
 * pessoa anota "papelão", outra "cartonado", outra "molhado" — e são a mesma percepção. Sem sinônimos, a
 * mesma cerveja aparece com três problemas diferentes, e nenhum deles acumula amostra suficiente para
 * virar tendência.
 *
 * <p><strong>O limiar só existe quando a fonte autoriza.</strong> Não é cautela jurídica performática: o
 * limiar é o resultado de trabalho experimental caro, e é por ele que os catálogos cobram. Descrever
 * "papelão" é vocabulário comum; afirmar o valor do limiar é reproduzir a medição de alguém.
 */
public final class SensoryDescriptor {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final String name;
    private final DescriptorCategory category;
    private final Set<String> synonyms;
    private final DescriptorSource source;
    private final BigDecimal perceptionThreshold;
    private final String thresholdUnit;
    private final List<Hypothesis> hypotheses;

    private SensoryDescriptor(UUID id, UUID breweryId, String code, String name,
            DescriptorCategory category, Set<String> synonyms, DescriptorSource source,
            BigDecimal perceptionThreshold, String thresholdUnit, List<Hypothesis> hypotheses) {
        this.id = id;
        this.breweryId = breweryId;
        this.code = code;
        this.name = name;
        this.category = category;
        this.synonyms = Set.copyOf(synonyms);
        this.source = source;
        this.perceptionThreshold = perceptionThreshold;
        this.thresholdUnit = thresholdUnit;
        this.hypotheses = List.copyOf(hypotheses);
    }

    public static SensoryDescriptor create(UUID id, UUID breweryId, String code, String name,
            DescriptorCategory category, Set<String> synonyms, DescriptorSource source,
            BigDecimal perceptionThreshold, String thresholdUnit, List<Hypothesis> hypotheses) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(source, "source");

        var normalizedCode = requireText(code, "code").toUpperCase(java.util.Locale.ROOT);
        var normalizedName = requireText(name, "name");

        // O limiar só entra se a fonte permitir. Recusar na criação, e não filtrar na leitura: um dado
        // que não pode ser publicado e mesmo assim está gravado é um vazamento esperando exportação.
        if (perceptionThreshold != null && !source.allowsThreshold()) {
            throw new ThresholdNotLicensedException(source.tier());
        }
        if (perceptionThreshold != null && (thresholdUnit == null || thresholdUnit.isBlank())) {
            // Limiar sem unidade é número sem significado: 0,1 pode ser µg/L, mg/L ou ppm, e a diferença
            // entre eles é de mil vezes.
            throw new IllegalArgumentException("limiar exige unidade");
        }

        return new SensoryDescriptor(id, breweryId, normalizedCode, normalizedName, category,
                normalizeSynonyms(synonyms), source, perceptionThreshold, thresholdUnit, hypotheses);
    }

    public static SensoryDescriptor reconstitute(UUID id, UUID breweryId, String code, String name,
            DescriptorCategory category, Set<String> synonyms, DescriptorSource source,
            BigDecimal perceptionThreshold, String thresholdUnit, List<Hypothesis> hypotheses) {
        return new SensoryDescriptor(id, breweryId, code, name, category, synonyms, source,
                perceptionThreshold, thresholdUnit, hypotheses);
    }

    /**
     * Se um termo digitado casa com este descritor.
     *
     * <p>Compara sem acento e sem caixa porque quem anota na mesa de prova escreve "cartonado" e
     * "Cartonádo" — e um vocabulário que só encontra o termo exato não serve para o momento em que ele é
     * usado, que é com a taça na mão.
     */
    public boolean matches(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        var normalized = normalize(term);
        return normalize(code).equals(normalized)
                || normalize(name).equals(normalized)
                || synonyms.stream().anyMatch(s -> normalize(s).equals(normalized));
    }

    /** O limiar, quando a fonte autoriza publicá-lo. */
    public Optional<BigDecimal> perceptionThreshold() {
        return Optional.ofNullable(perceptionThreshold);
    }

    public Optional<String> thresholdUnit() {
        return Optional.ofNullable(thresholdUnit);
    }

    /**
     * Se este descritor pode sair da cervejaria numa exportação.
     *
     * <p>O vocabulário licenciado para uso interno serve para anotar e comparar; republicá-lo num
     * relatório que vai ao cliente é outra coisa.
     */
    public boolean exportable() {
        return source.tier() != LicenseTier.LICENSED_INTERNAL_ONLY;
    }

    private static Set<String> normalizeSynonyms(Set<String> synonyms) {
        return synonyms == null ? Set.of()
                : synonyms.stream().filter(s -> s != null && !s.isBlank()).map(String::trim)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.trim().toLowerCase(java.util.Locale.ROOT),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String requireText(String value, String field) {
        var trimmed = Objects.requireNonNull(value, field).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public DescriptorCategory category() {
        return category;
    }

    public Set<String> synonyms() {
        return synonyms;
    }

    public DescriptorSource source() {
        return source;
    }

    public List<Hypothesis> hypotheses() {
        return hypotheses;
    }

    /** Limiar gravado sem que a licença da fonte permita publicá-lo. */
    public static final class ThresholdNotLicensedException extends RuntimeException {

        ThresholdNotLicensedException(LicenseTier tier) {
            super("a licença " + tier + " não autoriza registrar limiar de percepção");
        }
    }
}
