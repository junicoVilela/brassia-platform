package br.com.brew.brassia.metrology.domain;

import java.util.Objects;

/**
 * Um passo aplicado na correção, com a fórmula e a versão que o produziram.
 *
 * <p>É o que atende ao critério "resultado mostra fórmula e versão": meses depois é preciso poder
 * dizer não só que o valor foi corrigido, mas por qual regra e em que versão dela.
 *
 * @param version versão do cálculo; para a curva é o certificado que a originou
 */
public record CorrectionStep(String name, String method, String version) {

    public CorrectionStep {
        name = require(name, "nome do passo");
        method = require(method, "método");
        version = require(version, "versão");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }

    public static CorrectionStep temperature(String method, String version) {
        return new CorrectionStep("temperature", method, version);
    }

    public static CorrectionStep curve(String method, String certificateNumber) {
        return new CorrectionStep("curve", method, certificateNumber);
    }
}
