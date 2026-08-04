package br.com.brew.brassia.sensory.adapter.inbound.web;

import br.com.brew.brassia.sensory.domain.AlreadyEvaluatedException;
import br.com.brew.brassia.sensory.domain.ResultsNotAvailableException;
import br.com.brew.brassia.sensory.domain.SessionNotOpenException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.Map;
import org.springframework.core.annotation.Order;
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
}
