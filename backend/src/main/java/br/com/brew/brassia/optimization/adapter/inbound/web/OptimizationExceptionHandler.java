package br.com.brew.brassia.optimization.adapter.inbound.web;

import br.com.brew.brassia.optimization.domain.UnknownOptimizationRunException;
import br.com.brew.brassia.optimization.domain.UnpublishedRecipeException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas da otimização (OPT-001).
 *
 * <p><strong>Inviabilidade não está aqui, e é deliberado.</strong> "Nenhuma combinação respeita estas
 * restrições" é um resultado, não um erro: chega como 201 com o corpo explicando quais restrições se
 * contradizem. Transformá-la em 4xx faria a tela tratá-la como falha e perder a informação que a torna
 * acionável.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = OptimizationController.class)
class OptimizationExceptionHandler {

    /** Receita sem versão publicada: a entrada precisa ser estável para o resultado ser reproduzível. */
    @ExceptionHandler(UnpublishedRecipeException.class)
    ProblemDetail handleUnpublished(UnpublishedRecipeException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "unpublished_recipe",
                ex.getMessage());
    }

    /** Estado que a corrida não permite — já aplicada, ou aplicação sobre corrida inviável. */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "illegal_optimization_state", ex.getMessage());
    }

    @ExceptionHandler(UnknownOptimizationRunException.class)
    ProblemDetail handleUnknown(UnknownOptimizationRunException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_optimization_run",
                "Corrida de otimização não encontrada nesta cervejaria.");
    }
}
