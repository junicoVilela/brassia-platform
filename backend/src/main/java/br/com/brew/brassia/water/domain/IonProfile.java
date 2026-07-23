package br.com.brew.brassia.water.domain;

import java.math.BigDecimal;

/**
 * Composição iônica de um laudo de água, em mg/L. Conjunto fixo de íons
 * cervejeiros; cada valor é não-negativo e dentro de um limite físico razoável —
 * daí "íons/unidades válidos".
 */
public record IonProfile(
        BigDecimal calcium,
        BigDecimal magnesium,
        BigDecimal sodium,
        BigDecimal sulfate,
        BigDecimal chloride,
        BigDecimal bicarbonate) {

    private static final BigDecimal MAX_MG_PER_L = new BigDecimal("10000");

    public IonProfile {
        calcium = require(calcium, "cálcio");
        magnesium = require(magnesium, "magnésio");
        sodium = require(sodium, "sódio");
        sulfate = require(sulfate, "sulfato");
        chloride = require(chloride, "cloreto");
        bicarbonate = require(bicarbonate, "bicarbonato");
    }

    private static BigDecimal require(BigDecimal value, String ion) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_MG_PER_L) > 0) {
            throw new IllegalArgumentException(ion + " deve estar entre 0 e 10000 mg/L");
        }
        return value;
    }
}
