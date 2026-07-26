package br.com.brew.brassia.planning.adapter.inbound.web;

import br.com.brew.brassia.planning.domain.ReleaseBlockedException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz bloqueios de liberação (BOP-002) em 409 Problem Details, expondo a lista
 * completa de bloqueios na extensão {@code blockers} ("falha lista bloqueios").
 */
@RestControllerAdvice
class PlanningExceptionHandler {

    @ExceptionHandler(ReleaseBlockedException.class)
    ProblemDetail handleReleaseBlocked(ReleaseBlockedException ex) {
        var problem = ProblemDetails.of(HttpStatus.CONFLICT, "release_blocked",
                "A ordem não pôde ser liberada; há bloqueios.");
        List<Map<String, String>> blockers = ex.blockers().stream()
                .map(b -> Map.of("code", b.code(), "message", b.message()))
                .toList();
        problem.setProperty("blockers", blockers);
        return problem;
    }
}
