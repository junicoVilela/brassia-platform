package br.com.brew.brassia.digitaltwin.adapter.inbound.web;

import br.com.brew.brassia.digitaltwin.application.service.ComputeProfileHandler;
import br.com.brew.brassia.digitaltwin.application.service.ControlChartService;
import br.com.brew.brassia.digitaltwin.domain.ControlLimits;
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

    /**
     * Histórico curto demais para limites de controle (SPC-001).
     *
     * <p>422: o pedido está bem formado, faltam dados. E a resposta diz **quantos faltam**, porque a
     * providência é concreta — medir mais, ou incluir mais lotes na amostra.
     *
     * <p>Recusar é melhor que devolver limites frouxos: limites calculados sobre cinco pontos passam
     * qualquer coisa, e um controle que nunca dispara parece um processo saudável.
     */
    @ExceptionHandler(ControlLimits.InsufficientHistoryException.class)
    ProblemDetail handleInsufficientHistory(ControlLimits.InsufficientHistoryException ex) {
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_control_history",
                "Não há medições suficientes para calcular limites de controle que signifiquem algo.");
        problem.setProperty("available", ex.available());
        problem.setProperty("required", ex.required());
        return problem;
    }

    /**
     * A série mistura unidades (SPC-001).
     *
     * <p>Converter em silêncio seria pior: a conversão pertence a quem registrou a medição, e uma carta
     * montada sobre °C e °F juntos produz limites que não descrevem processo nenhum.
     */
    @ExceptionHandler(ControlChartService.MixedUnitsException.class)
    ProblemDetail handleMixedUnits(ControlChartService.MixedUnitsException ex) {
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "mixed_units_in_series",
                "As medições selecionadas estão em unidades diferentes. "
                        + "Padronize a unidade no registro antes de analisar a série.");
        problem.setProperty("kind", ex.kind());
        return problem;
    }

}
