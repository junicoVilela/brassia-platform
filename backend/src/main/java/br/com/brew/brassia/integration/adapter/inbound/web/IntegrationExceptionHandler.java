package br.com.brew.brassia.integration.adapter.inbound.web;

import br.com.brew.brassia.integration.domain.UnknownSubscriptionException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas das integrações (INT-002). O {@code @Order} vence o catch-all do advice global. */
@Order(0)
@RestControllerAdvice
class IntegrationExceptionHandler {

    /** Mesma resposta para "não existe" e "é de outra cervejaria". */
    @ExceptionHandler(UnknownSubscriptionException.class)
    ProblemDetail handleUnknownSubscription(UnknownSubscriptionException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_webhook_subscription",
                "Esta assinatura de webhook não existe nesta cervejaria.");
        problem.setProperty("subscriptionId", ex.subscriptionId().toString());
        return problem;
    }
}
