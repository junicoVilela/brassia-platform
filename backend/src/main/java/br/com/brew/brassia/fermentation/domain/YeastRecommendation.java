package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recomendação de reutilização de uma coleta (YST-002). Cada fator — geração, idade e
 * viabilidade — entra com seu próprio veredito e sua própria frase, para a recomendação ser
 * explicável: quem lê vê por que a coleta foi recomendada ou barrada, não só uma nota.
 *
 * <p>Recomendar não é usar: o pitch continua exigindo confirmação humana e lote vinculado.
 */
public record YeastRecommendation(
        YeastHarvest harvest,
        boolean recommended,
        long ageDays,
        List<Factor> factors) {

    /** Um fator avaliado, com o limite aplicado e a explicação em português. */
    public record Factor(String name, boolean withinPolicy, String explanation) {}

    public YeastRecommendation {
        factors = List.copyOf(factors);
    }

    /** Motivos que barram a coleta; vazio quando ela é recomendada. */
    public List<String> blockers() {
        return factors.stream().filter(f -> !f.withinPolicy()).map(Factor::explanation).toList();
    }

    /**
     * Avalia uma coleta disponível contra a política. Coleta indisponível nunca é avaliada
     * aqui — quem filtra é o caso de uso, porque em quarentena ou reprovada ela sequer é
     * candidata.
     */
    public static YeastRecommendation evaluate(YeastHarvest harvest, YeastPolicy policy, Instant now) {
        var ageDays = Duration.between(harvest.harvestedAt(), now).toDays();
        var factors = new ArrayList<Factor>();

        var generationOk = harvest.generation() <= policy.maxGeneration();
        factors.add(new Factor("generation", generationOk,
                "Geração " + harvest.generation() + " de no máximo " + policy.maxGeneration()
                        + (generationOk ? "" : " — linhagem esgotada")));

        var ageOk = ageDays <= policy.maxAgeDays();
        factors.add(new Factor("age", ageOk,
                "Coletada há " + ageDays + " dia(s), limite de " + policy.maxAgeDays()
                        + (ageOk ? "" : " — levedura velha demais")));

        var viabilityOk = harvest.viabilityPercent().compareTo(policy.minViabilityPercent()) >= 0;
        factors.add(new Factor("viability", viabilityOk,
                "Viabilidade de " + harvest.viabilityPercent().stripTrailingZeros().toPlainString()
                        + "%, mínimo de " + policy.minViabilityPercent().stripTrailingZeros().toPlainString() + "%"
                        + (viabilityOk ? "" : " — abaixo do mínimo")));

        var recommended = factors.stream().allMatch(Factor::withinPolicy);
        return new YeastRecommendation(harvest, recommended, ageDays, factors);
    }

    /**
     * Ordena as candidatas: recomendadas primeiro e, dentro delas, a de melhor prognóstico —
     * maior viabilidade, depois menor geração, depois mais nova.
     */
    public static List<YeastRecommendation> rank(List<YeastRecommendation> recommendations) {
        return recommendations.stream()
                .sorted(Comparator.comparing(YeastRecommendation::recommended).reversed()
                        .thenComparing(r -> r.harvest().viabilityPercent(), Comparator.reverseOrder())
                        .thenComparingInt(r -> r.harvest().generation())
                        .thenComparingLong(YeastRecommendation::ageDays))
                .toList();
    }

    /** Percentual de viabilidade perdido em relação ao mínimo aceitável, para leitura rápida. */
    public BigDecimal viabilityMargin(YeastPolicy policy) {
        return harvest.viabilityPercent().subtract(policy.minViabilityPercent())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
