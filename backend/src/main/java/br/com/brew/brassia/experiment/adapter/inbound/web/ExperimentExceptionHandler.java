package br.com.brew.brassia.experiment.adapter.inbound.web;

import br.com.brew.brassia.experiment.domain.ConfoundedExperimentException;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import br.com.brew.brassia.experiment.domain.InvalidExperimentSubjectException;
import br.com.brew.brassia.experiment.domain.UnknownExperimentException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas dos experimentos (EXP-001).
 *
 * <p><strong>Limitado a este controller de propósito.</strong> O tratador captura
 * {@link DuplicateKeyException}, que é genérica do Spring — sem o escopo, este módulo passaria a responder
 * pelas violações de unicidade de todos os outros, traduzindo-as para uma mensagem sobre experimentos.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = ExperimentController.class)
class ExperimentExceptionHandler {

    /**
     * O par de lotes já está em outro experimento ativo.
     *
     * <p>Chega como violação de índice porque quem decide é o PostgreSQL: entre duas requisições
     * simultâneas, só o banco sabe qual chegou primeiro. Verificar antes em Java deixaria a janela aberta
     * — e dois experimentos sobre o mesmo par significam que nenhuma das duas variáveis está isolada.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDuplicatePair(DuplicateKeyException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "experiment_pair_already_active",
                "Estes dois lotes já estão em um experimento em andamento. Dois experimentos sobre o "
                        + "mesmo par testam variáveis diferentes nos mesmos lotes, e aí nenhuma das duas "
                        + "está isolada. Conclua ou abandone o experimento anterior.");
    }

    /**
     * Mais de uma variável — ou nenhuma.
     *
     * <p>422 e não 400: a requisição está bem formada. O que não se sustenta é o <em>desenho</em>, e a
     * resposta devolve os fatores que diferem porque a correção é escolher qual deles isolar — informação
     * que a pessoa já tem na tela, mas que o cliente da API não teria de outro modo.
     */
    @ExceptionHandler(ConfoundedExperimentException.class)
    ProblemDetail handleConfounded(ConfoundedExperimentException ex) {
        var detail = ex.differingFactors().isEmpty()
                ? "Controle e variante estão idênticos: não há variável em teste."
                : "Mais de um fator difere entre controle e variante. Com dois fatores, qualquer "
                        + "resultado tem duas explicações e nenhuma pode ser descartada.";
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "confounded_experiment", detail);
        problem.setProperty("differingFactors", ex.differingFactors());
        return problem;
    }

    /** Os lotes escolhidos não servem: outra receita, ou inexistentes nesta cervejaria. */
    @ExceptionHandler(InvalidExperimentSubjectException.class)
    ProblemDetail handleInvalidSubject(InvalidExperimentSubjectException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_experiment_subject",
                ex.getMessage());
    }

    /**
     * Transição que o estado não permite.
     *
     * <p>409: não é erro de quem chamou, é o experimento estar em outro ponto — tipicamente porque outra
     * pessoa já concluiu. A resposta diz o estado atual para a tela poder se atualizar em vez de insistir.
     */
    @ExceptionHandler(ExperimentPlan.IllegalExperimentTransitionException.class)
    ProblemDetail handleTransition(ExperimentPlan.IllegalExperimentTransitionException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "illegal_experiment_transition",
                "O experimento está em " + ex.current() + " e não pode ir para " + ex.attempted() + ".");
        problem.setProperty("currentStatus", ex.current().name());
        problem.setProperty("attemptedStatus", ex.attempted().name());
        return problem;
    }

    @ExceptionHandler(UnknownExperimentException.class)
    ProblemDetail handleUnknown(UnknownExperimentException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_experiment",
                "Experimento não encontrado nesta cervejaria.");
    }
}
