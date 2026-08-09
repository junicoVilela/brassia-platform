package br.com.brew.brassia.digitaltwin.adapter.inbound.web;

import br.com.brew.brassia.digitaltwin.application.service.ComputeProfileHandler;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do perfil aprendido (DTW-001). */
@Order(0)
@RestControllerAdvice
class DigitalTwinExceptionHandler {

    /**
     * Nenhum lote da amostra serviu.
     *
     * <p>422 e não 400: a requisição está bem formada — os ids vieram, são UUIDs. O que não serve é a
     * <em>amostra</em>, e a distinção importa para quem opera: 400 mandaria conferir o formato da chamada,
     * e o problema está nos lotes escolhidos.
     *
     * <p>A mensagem diz o que fazer, porque a causa quase sempre é a mesma: os lotes ainda não foram
     * transferidos, ou são de outra receita.
     */
    @ExceptionHandler(ComputeProfileHandler.EmptySampleException.class)
    ProblemDetail handleEmptySample(ComputeProfileHandler.EmptySampleException ex) {
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "empty_learning_sample",
                "Nenhum dos lotes informados serve para aprender sobre esta receita. "
                        + "Só entram lotes desta receita que já foram transferidos.");
        problem.setProperty("recipeId", ex.recipeId().toString());
        return problem;
    }
}
