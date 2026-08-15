package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Quanto se admite perder, por etapa (CST-002-A).
 *
 * <p><strong>Na receita, e não no equipamento.</strong> A perda característica é da cerveja tanto quanto
 * do tanque: uma IPA muito lupulada deixa mais líquido preso no trub que uma lager no mesmo fermentador.
 * O dead space do equipamento já é conhecido em outro lugar; o que falta é o que esta cerveja perde.
 *
 * <p><strong>Percentual, e não litros.</strong> Perda de trub e de absorção de lúpulo escala com o
 * tamanho da brassa. Um valor absoluto ficaria errado no dia em que a cervejaria dobrasse o lote — e
 * ficaria errado em silêncio.
 *
 * <p><strong>Vazio é legítimo.</strong> Quem ainda não mediu a própria perda não tem esperado, e a
 * variação volta a mostrar a perda como fato, declarando a lacuna. Assumir zero faria toda perda parecer
 * desvio.
 */
public record ExpectedLosses(BigDecimal transferPercent, BigDecimal packagingPercent) {

    public ExpectedLosses {
        validate(transferPercent, "perda esperada na transferência");
        validate(packagingPercent, "perda esperada no envase");
    }

    public static ExpectedLosses none() {
        return new ExpectedLosses(null, null);
    }

    private static void validate(BigDecimal percent, String field) {
        if (percent == null) {
            return;
        }
        if (percent.signum() < 0) {
            // Perda negativa seria cerveja aparecendo do nada.
            throw new IllegalArgumentException(field + " não pode ser negativa");
        }
        if (percent.compareTo(new BigDecimal("100")) >= 0) {
            // 100% é um lote que não chega ao fermentador: não é perda esperada, é erro de digitação.
            throw new IllegalArgumentException(field + " precisa ser menor que 100%");
        }
    }

    public Optional<BigDecimal> transfer() {
        return Optional.ofNullable(transferPercent);
    }

    public Optional<BigDecimal> packaging() {
        return Optional.ofNullable(packagingPercent);
    }
}
