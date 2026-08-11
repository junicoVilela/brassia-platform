package br.com.brew.brassia.blend.adapter.inbound.web;

import br.com.brew.brassia.blend.domain.BlendOperation;
import br.com.brew.brassia.blend.domain.UnknownBlendBatchException;
import br.com.brew.brassia.blend.domain.UnknownBlendOperationException;
import br.com.brew.brassia.blend.domain.UnbalancedBlendException;
import br.com.brew.brassia.production.BlendResultCommands;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do blend (BLD-001). */
@Order(0)
@RestControllerAdvice
class BlendExceptionHandler {

    /**
     * O balanço não fecha.
     *
     * <p>422 e não 400: os números vieram bem formados, é a <em>conta</em> que não fecha. A resposta
     * devolve as três parcelas e a diferença porque a correção é aritmética — quem opera precisa saber se
     * faltam 12 litros ou 120, e de que lado.
     */
    /**
     * Sai mais cerveja do que o lote tem.
     *
     * <p>422 pelo mesmo motivo do balanço: o pedido está bem formado e é a realidade do tanque que o
     * recusa. A resposta diz quanto existe e quanto foi pedido — sem os dois números, quem opera só sabe
     * que falhou, e refaz a operação no chute até passar.
     *
     * <p>A checagem vive na execução, e não na simulação, porque é a execução que move: entre simular e
     * executar, um envase pode ter esvaziado o lote que a simulação viu cheio.
     */
    /**
     * O tanque de destino já tem cerveja.
     *
     * <p>409 e não 422: não é a conta que está errada, é o mundo que mudou — outro lote ocupou o tanque
     * entre planejar e executar. A resposta nomeia o ocupante porque "tanque ocupado" sozinho manda quem
     * opera procurar em todas as telas qual lote está lá.
     */
    @ExceptionHandler(BlendResultCommands.VesselOccupiedException.class)
    ProblemDetail handleVesselOccupied(BlendResultCommands.VesselOccupiedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "vessel_occupied",
                "O tanque de destino já tem um lote fermentando. Escolha outro tanque ou libere este.");
        problem.setProperty("equipmentId", ex.equipmentId());
        problem.setProperty("occupiedBy", ex.occupiedBy());
        return problem;
    }

    @ExceptionHandler(BlendResultCommands.InsufficientBatchVolumeException.class)
    ProblemDetail handleInsufficientVolume(BlendResultCommands.InsufficientBatchVolumeException ex) {
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_batch_volume",
                "O lote tem " + ex.availableLiters() + " L e a operação tira " + ex.requestedLiters()
                        + " L. Confira o volume medido ou o lote de origem.");
        problem.setProperty("batchId", ex.batchId());
        problem.setProperty("availableLiters", ex.availableLiters());
        problem.setProperty("requestedLiters", ex.requestedLiters());
        return problem;
    }

    @ExceptionHandler(UnbalancedBlendException.class)
    ProblemDetail handleUnbalanced(UnbalancedBlendException ex) {
        var sumiu = ex.difference().signum() > 0;
        var problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "unbalanced_blend",
                sumiu
                        ? "Falta explicar " + ex.difference().abs() + " L: cerveja que entra e não sai foi "
                                + "para algum lugar. Declare a perda ou confira os volumes."
                        : "Sobram " + ex.difference().abs() + " L na saída: não se cria volume numa "
                                + "operação de blend. Confira os volumes medidos.");
        problem.setProperty("inputLiters", ex.inputLiters());
        problem.setProperty("outputLiters", ex.outputLiters());
        problem.setProperty("declaredLossLiters", ex.declaredLoss());
        problem.setProperty("difference", ex.difference());
        return problem;
    }

    /** Lote inexistente nesta cervejaria. Conferido na simulação — depois de aprovar já é tarde. */
    @ExceptionHandler(UnknownBlendBatchException.class)
    ProblemDetail handleUnknownBatch(UnknownBlendBatchException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "unknown_blend_batch",
                ex.getMessage());
    }

    /**
     * Transição que o estado não permite.
     *
     * <p>409: tipicamente outra pessoa já executou. A resposta diz o estado atual para a tela se atualizar
     * em vez de insistir — e insistir aqui significaria misturar cerveja duas vezes.
     */
    @ExceptionHandler(BlendOperation.IllegalBlendTransitionException.class)
    ProblemDetail handleTransition(BlendOperation.IllegalBlendTransitionException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "illegal_blend_transition",
                "A operação está em " + ex.current() + " e não pode ir para " + ex.attempted() + ".");
        problem.setProperty("currentStatus", ex.current().name());
        problem.setProperty("attemptedStatus", ex.attempted().name());
        return problem;
    }

    @ExceptionHandler(UnknownBlendOperationException.class)
    ProblemDetail handleUnknown(UnknownBlendOperationException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_blend_operation",
                "Operação de blend não encontrada nesta cervejaria.");
    }
}
