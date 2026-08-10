package br.com.brew.brassia.sensory.domain;

import java.util.Objects;

/**
 * Uma causa possível e o que se faria a respeito (SEN-002).
 *
 * <p><strong>O nome do tipo é a garantia.</strong> O critério da história diz que causa e ação corretiva
 * são <em>hipóteses, não diagnóstico automático</em> — e a diferença desaparece na hora em que alguém lê
 * "diacetil → parada de fermentação" numa tela e vai mexer no tanque.
 *
 * <p>Chamar isto de {@code Diagnosis} ou {@code Cause} faria o mesmo dado significar outra coisa para quem
 * lê o código e, depois, para quem lê a tela. É a mesma decisão de {@code Estimate}/{@code supported} em
 * DTW-001 e OPT-001: o nome carrega o limite epistêmico do dado.
 *
 * <p>{@link #confidence} não é probabilidade calculada — é o quanto a literatura associa a causa ao
 * descritor. Um número aqui daria falsa precisão a algo que ninguém mediu nesta cervejaria.
 */
public record Hypothesis(String possibleCause, String suggestedCheck, Likelihood likelihood) {

    public Hypothesis {
        possibleCause = requireText(possibleCause, "possibleCause");
        // A verificação sugerida é obrigatória, e é o que separa hipótese de palpite: dizer "pode ser
        // infecção" sem dizer como confirmar deixa quem lê com a preocupação e sem o próximo passo.
        suggestedCheck = requireText(suggestedCheck, "suggestedCheck");
        Objects.requireNonNull(likelihood, "likelihood");
    }

    /** O quanto a literatura associa esta causa a este descritor. Não é medição desta cervejaria. */
    public enum Likelihood {
        /** Associação frequente e bem documentada. */
        COMMON,
        /** Acontece, e vale descartar antes de procurar mais longe. */
        OCCASIONAL,
        /** Raro; considerar quando as outras hipóteses não se sustentaram. */
        RARE
    }

    private static String requireText(String value, String field) {
        var trimmed = Objects.requireNonNull(value, field).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return trimmed;
    }
}
