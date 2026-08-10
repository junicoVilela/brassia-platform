package br.com.brew.brassia.optimization.application.port.inbound;

import br.com.brew.brassia.optimization.domain.Objective;
import br.com.brew.brassia.optimization.domain.OptimizationConstraint;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import java.util.List;
import java.util.UUID;

/** Otimizar e registrar a aplicação (OPT-001). */
public interface OptimizationCommands {

    OptimizationRun optimize(OptimizeCommand command);

    /**
     * Anexa a explicação em linguagem natural.
     *
     * <p>Comando separado de propósito: a explicação chega <em>depois</em> do resultado existir, e não
     * pode influenciá-lo. Gerá-la dentro da otimização deixaria a fronteira dependendo de disciplina de
     * quem escreve o código, em vez da forma do contrato.
     */
    OptimizationRun explain(UUID breweryId, UUID runId, String explanation, UUID actor);

    /** Registra que uma alternativa virou versão nova de receita — criada por fora, sob revisão. */
    OptimizationRun markApplied(UUID breweryId, UUID runId, UUID recipeVersionId, UUID actor);

    /**
     * @param objective   um só. Custo, disponibilidade e alvo técnico se contradizem, e "otimizar tudo"
     *                    entregaria uma média ponderada por pesos que ninguém escolheu
     * @param constraints restrições explícitas; violá-las descarta a candidata em vez de penalizá-la
     */
    record OptimizeCommand(UUID breweryId, UUID recipeId, Objective objective,
            List<OptimizationConstraint> constraints, UUID actor) {
    }
}
