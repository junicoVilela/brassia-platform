package br.com.brew.brassia.sensory.adapter.inbound.web;

import br.com.brew.brassia.sensory.domain.AlreadyEvaluatedException;
import br.com.brew.brassia.sensory.domain.ResultsNotAvailableException;
import br.com.brew.brassia.sensory.domain.SessionNotOpenException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.Map;
import org.springframework.core.annotation.Order;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas da análise sensorial (SEN-001) — todas em defesa da cegueira. */
@Order(0)
@RestControllerAdvice
class SensoryExceptionHandler {

    /** O critério da história: nada de resultado antes do fechamento. */
    @ExceptionHandler(ResultsNotAvailableException.class)
    ProblemDetail handleResults(ResultsNotAvailableException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "results_not_available", ex.getMessage());
        problem.setProperty("session", Map.of("code", ex.sessionCode(), "status", ex.status()));
        return problem;
    }

    @ExceptionHandler(SessionNotOpenException.class)
    ProblemDetail handleNotOpen(SessionNotOpenException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "session_not_open", ex.getMessage());
        problem.setProperty("session", Map.of("code", ex.sessionCode(), "status", ex.status()));
        return problem;
    }

    @ExceptionHandler(AlreadyEvaluatedException.class)
    ProblemDetail handleAlreadyEvaluated(AlreadyEvaluatedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "already_evaluated", ex.getMessage());
        problem.setProperty("sample", Map.of("blindCode", ex.blindCode()));
        return problem;
    }

    /**
     * Limiar registrado sem licença que o autorize (SEN-002).
     *
     * <p>422 e não 400: o pedido está bem formado — o número veio, a unidade veio. O que não se pode é
     * <em>publicar</em> aquele dado a partir daquela fonte, e a mensagem diz o que fazer, porque as duas
     * saídas são diferentes: trocar a fonte, ou cadastrar o descritor sem o limiar.
     */
    @ExceptionHandler(SensoryDescriptor.ThresholdNotLicensedException.class)
    ProblemDetail handleThresholdNotLicensed(SensoryDescriptor.ThresholdNotLicensedException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "threshold_not_licensed",
                "A licença desta fonte não autoriza registrar limiar de percepção. "
                        + "Use uma fonte que permita o dado quantitativo, ou cadastre o descritor sem o "
                        + "limiar — o vocabulário continua utilizável.");
    }
}