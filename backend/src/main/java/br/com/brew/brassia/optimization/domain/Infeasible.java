package br.com.brew.brassia.optimization.domain;

import java.util.List;
import java.util.Objects;

/**
 * Por que não há solução (OPT-001).
 *
 * <p><strong>Inviabilidade é resposta, não erro.</strong> "Não existe combinação que respeite estas
 * restrições" é informação acionável — e mais honesta que devolver a melhor violação disfarçada de ótimo.
 *
 * <p>O que torna a resposta útil é dizer <em>quais</em> restrições se contradizem. "Inviável" sozinho manda
 * a pessoa afrouxar tudo ao acaso; saber que o teto de custo e a faixa de IBU não coexistem transforma
 * isso numa decisão.
 */
public record Infeasible(List<String> conflictingConstraints, String explanation) {

    public Infeasible {
        conflictingConstraints = List.copyOf(
                Objects.requireNonNull(conflictingConstraints, "conflictingConstraints"));
        Objects.requireNonNull(explanation, "explanation");
    }
}
