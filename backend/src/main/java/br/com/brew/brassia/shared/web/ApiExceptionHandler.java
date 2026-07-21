package br.com.brew.brassia.shared.web;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz exceções da camada web em Problem Details (RFC 9457) com {@code code}
 * e {@code traceId}. Nunca expõe stack trace, SQL ou mensagem interna sensível:
 * o {@code detail} é sempre uma frase segura e estável.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(
                HttpStatus.BAD_REQUEST, "validation_error", "Um ou mais campos são inválidos.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toFieldError)
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "validation_error", "Um ou mais campos são inválidos.");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException ex) {
        return ProblemDetails.of(
                HttpStatus.CONFLICT, "conflict", "A operação conflita com o estado atual do recurso.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "bad_request", "Requisição inválida.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        logger.error("Erro inesperado (traceId=" + ProblemDetails.currentTraceId() + ")", ex);
        return ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Ocorreu um erro inesperado.");
    }

    private static Map<String, String> toFieldError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "inválido" : error.getDefaultMessage();
        return Map.of("field", error.getField(), "message", message);
    }
}
