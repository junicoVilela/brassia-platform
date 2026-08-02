package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recomendação de validade a partir da evidência de oxigênio (FSL-001).
 *
 * <p>Cada fator entra com seu próprio veredito e sua própria frase, para a recomendação ser
 * explicável: quem lê vê por que aqueles dias foram sugeridos, não só a data. Purga não conferida
 * e vedação reprovada não mudam o número — mudam a confiança nele, e isso é dito em voz alta.
 *
 * <p>Recomendar não é decidir: a validade que vale é a que um humano confirma ou sobrepõe.
 */
public record ShelfLifeRecommendation(int shelfLifeDays, LocalDate bestBefore, BigDecimal totalPackageOxygenPpb,
        BigDecimal matchedTierMaxTpoPpb, boolean withinPolicyTiers, List<Factor> factors) {

    /** Um fator avaliado, com o limite aplicado e a explicação em português. */
    public record Factor(String name, boolean trustworthy, String explanation) {}

    public ShelfLifeRecommendation {
        factors = List.copyOf(Objects.requireNonNull(factors, "factors"));
        Objects.requireNonNull(bestBefore, "validade recomendada é obrigatória");
        Objects.requireNonNull(totalPackageOxygenPpb, "TPO é obrigatório");
    }

    /** Ressalvas que reduzem a confiança na recomendação; vazio quando a evidência está completa. */
    public List<String> caveats() {
        return factors.stream().filter(f -> !f.trustworthy()).map(Factor::explanation).toList();
    }

    /**
     * Avalia as medições contra a política da casa. O TPO define os dias; purga e vedação entram
     * como qualidade da evidência.
     */
    public static ShelfLifeRecommendation evaluate(OxygenMeasurement measurement, ShelfLifePolicy policy,
            LocalDate packagedOn) {
        Objects.requireNonNull(measurement, "medição é obrigatória");
        Objects.requireNonNull(policy, "política é obrigatória");
        Objects.requireNonNull(packagedOn, "data do envase é obrigatória");

        var tpo = measurement.totalPackageOxygenPpb();
        var tier = policy.tierFor(tpo);
        var days = tier.map(ShelfLifePolicy.Tier::shelfLifeDays).orElse(policy.fallbackDays());

        var factors = new ArrayList<Factor>();
        factors.add(new Factor("tpo", tier.isPresent(),
                tier.map(t -> "TPO de " + plain(tpo) + " ppb dentro da faixa de até "
                                + plain(t.maxTpoPpb()) + " ppb, que sustenta " + t.shelfLifeDays() + " dia(s)")
                        .orElse("TPO de " + plain(tpo) + " ppb acima de todas as faixas da política — "
                                + "aplicado o pior caso de " + policy.fallbackDays() + " dia(s)")));
        factors.add(new Factor("dissolvedOxygen", true,
                "Oxigênio dissolvido de " + plain(measurement.dissolvedOxygenPpb()) + " ppb; espaço livre "
                        + "responde por " + plain(measurement.headspaceOxygenPpb()) + " ppb"));
        factors.add(new Factor("purge", measurement.purgeVerified(),
                measurement.purgeVerified()
                        ? "Purga conferida: " + measurement.purgeMethod()
                        : "Purga não conferida (" + measurement.purgeMethod() + ") — a medição pode não "
                                + "representar o lote inteiro"));
        factors.add(new Factor("seal", measurement.sealCheckPassed(),
                measurement.sealCheckPassed()
                        ? "Vedação aprovada: " + measurement.sealCheckMethod()
                        : "Vedação reprovada (" + measurement.sealCheckMethod() + ") — embalagem que vaza "
                                + "entra oxigênio depois do envase e a validade não se sustenta"));

        return new ShelfLifeRecommendation(days, packagedOn.plusDays(days), tpo,
                tier.map(ShelfLifePolicy.Tier::maxTpoPpb).orElse(null), tier.isPresent(), factors);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
