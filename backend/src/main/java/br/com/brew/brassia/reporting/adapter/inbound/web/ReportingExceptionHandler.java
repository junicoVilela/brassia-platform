package br.com.brew.brassia.reporting.adapter.inbound.web;

import br.com.brew.brassia.reporting.domain.UnknownBatchReportException;
import br.com.brew.brassia.reporting.domain.UnknownSavedReportException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do relatório (RPT-001). O {@code @Order} vence o catch-all do advice global. */
@Order(0)
@RestControllerAdvice
class ReportingExceptionHandler {

    @ExceptionHandler(UnknownBatchReportException.class)
    ProblemDetail handleUnknownBatch(UnknownBatchReportException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_batch",
                "Este lote não existe nesta cervejaria.");
        problem.setProperty("batchId", ex.batchId().toString());
        return problem;
    }

    @ExceptionHandler(UnknownSavedReportException.class)
    ProblemDetail handleUnknownSavedReport(UnknownSavedReportException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_saved_report",
                "Este relatório salvo não existe nesta cervejaria.");
        problem.setProperty("id", ex.id().toString());
        return problem;
    }
}
