package br.com.brew.brassia.quality.adapter.inbound.web;

import br.com.brew.brassia.quality.domain.CriticalPointInstrumentException;
import br.com.brew.brassia.quality.domain.PlanNotPublishedException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas de qualidade (QLT-001), sempre dizendo o que falta para a medição valer. */
@RestControllerAdvice
class QualityExceptionHandler {

    /**
     * Ponto crítico com instrumento não apto. Devolvemos a aptidão junto: sem ela a pessoa não
     * sabe se recalibra, se desbloqueia ou se troca de instrumento.
     */
    @ExceptionHandler(CriticalPointInstrumentException.class)
    ProblemDetail handleCriticalPoint(CriticalPointInstrumentException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "instrument_not_fit", ex.getMessage());
        problem.setProperty("controlPoint", Map.of(
                "parameter", ex.parameter(),
                "instrument", ex.instrumentCode(),
                "fitness", ex.fitness()));
        return problem;
    }

    @ExceptionHandler(PlanNotPublishedException.class)
    ProblemDetail handleDraftPlan(PlanNotPublishedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "plan_not_published", ex.getMessage());
        problem.setProperty("plan", Map.of("code", ex.planCode()));
        return problem;
    }
}
