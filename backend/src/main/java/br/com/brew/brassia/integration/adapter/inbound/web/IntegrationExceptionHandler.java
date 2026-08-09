package br.com.brew.brassia.integration.adapter.inbound.web;

import br.com.brew.brassia.integration.domain.UnknownScanCodeException;
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

    /**
     * Código ilegível ou de tipo desconhecido (INT-003).
     *
     * <p>422 e não 400: a requisição está bem formada — o parâmetro veio, é uma string —, o que não serve é
     * o <em>conteúdo</em> lido. A distinção importa para quem opera: 400 mandaria procurar o problema no
     * aplicativo que fez a chamada, e o problema está na etiqueta.
     *
     * <p>A mensagem é a mesma para todos os motivos. Distinguir "formato inválido" de "tipo que não existe"
     * ensinaria a quem estivesse sondando quais tipos o sistema conhece.
     */
    @ExceptionHandler(UnknownScanCodeException.class)
    ProblemDetail handleUnknownScanCode(UnknownScanCodeException ex) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "unknown_scan_code",
                "Este código não é reconhecido pelo sistema.");
    }

    /** Mesma resposta para "não existe" e "é de outra cervejaria". */
    @ExceptionHandler(UnknownSubscriptionException.class)
    ProblemDetail handleUnknownSubscription(UnknownSubscriptionException ex) {
        var problem = ProblemDetails.of(HttpStatus.NOT_FOUND, "unknown_webhook_subscription",
                "Esta assinatura de webhook não existe nesta cervejaria.");
        problem.setProperty("subscriptionId", ex.subscriptionId().toString());
        return problem;
    }
}
