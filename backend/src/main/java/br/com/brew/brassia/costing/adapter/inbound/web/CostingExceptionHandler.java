package br.com.brew.brassia.costing.adapter.inbound.web;

import br.com.brew.brassia.costing.domain.UnknownBatchCostException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do custo (CST-001). O {@code @Order} vence o catch-all do advice global. */
@Order(0)
@RestControllerAdvice
class CostingExceptionHandler {

    @ExceptionHandler(UnknownBatchCostException.class)
    ProblemDetail handleUnknownBatch(UnknownBatchCostException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_batch",
                "Este lote não existe nesta cervejaria.");
        problem.setProperty("batchId", ex.batchId().toString());
        return problem;
    }
}
