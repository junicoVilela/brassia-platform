package br.com.brew.brassia.experiment.domain;

import java.util.Objects;

/**
 * Um fator do experimento: o que vale no controle e o que vale na variante (EXP-001).
 *
 * <p>Os fatores <strong>iguais</strong> são declarados junto com o que difere, e isso é deliberado. Um
 * experimento que lista só a variável mudada não deixa ninguém conferir se o resto ficou mesmo igual —
 * e "o resto ficou igual" é a afirmação sobre a qual toda a conclusão se apoia. Declarar mesma levedura,
 * mesma água e mesmo tanque é o que permite, meses depois, alguém notar que o tanque na verdade era outro.
 */
public record ExperimentFactor(String name, String controlValue, String variantValue) {

    public ExperimentFactor {
        name = requireText(name, "name");
        controlValue = requireText(controlValue, "controlValue");
        variantValue = requireText(variantValue, "variantValue");
    }

    /** Se este fator difere entre os dois lados. */
    public boolean differs() {
        return !controlValue.equals(variantValue);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return trimmed;
    }
}
