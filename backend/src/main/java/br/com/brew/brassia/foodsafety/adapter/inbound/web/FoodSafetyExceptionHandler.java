package br.com.brew.brassia.foodsafety.adapter.inbound.web;

import br.com.brew.brassia.foodsafety.domain.UnknownAllergenException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas da segurança de alimentos (FDS-001).
 *
 * <p>{@code @Order(0)} declara precedência sobre o catch-all do advice global, que sem isso venceria
 * por ordem alfabética de pacote e transformaria a recusa em 500 — o mesmo defeito que a
 * rastreabilidade descobriu na TRC-001.
 */
@Order(0)
@RestControllerAdvice
class FoodSafetyExceptionHandler {

    @ExceptionHandler(UnknownAllergenException.class)
    ProblemDetail handleUnknownAllergen(UnknownAllergenException ex) {
        var problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "unknown_allergen",
                "O alergênico declarado não está no cadastro da cervejaria.");
        problem.setProperty("allergen", ex.code().value());
        return problem;
    }
}
