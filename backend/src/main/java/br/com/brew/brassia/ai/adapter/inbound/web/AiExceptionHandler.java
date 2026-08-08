package br.com.brew.brassia.ai.adapter.inbound.web;

import br.com.brew.brassia.ai.domain.AiBudgetExceededException;
import br.com.brew.brassia.ai.domain.AiUnavailableException;
import br.com.brew.brassia.ai.domain.InvalidModelResponseException;
import br.com.brew.brassia.ai.domain.StaleAiBudgetException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Recusas do gateway de IA (AIA-001). O {@code @Order} vence o catch-all do advice global.
 *
 * <p>Os quatro status são escolhidos para dizer <em>de quem é a providência</em>, não só que deu errado:
 *
 * <ul>
 *   <li><strong>501</strong> provedor desligado — decisão de instalação; nada a tentar de novo.
 *   <li><strong>503</strong> provedor fora do ar — transitório; tentar depois faz sentido.
 *   <li><strong>402</strong> orçamento esgotado — quem tem alçada sobe o teto ou espera o mês virar.
 *   <li><strong>502</strong> resposta fora do contrato — o erro é do outro lado da fronteira, e repetir
 *       não resolve: o prompt ou o schema precisa mudar.
 * </ul>
 *
 * <p>Nenhuma das mensagens carrega prompt, resposta ou trecho de documento.
 */
@Order(0)
@RestControllerAdvice
class AiExceptionHandler {

    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail handleUnavailable(AiUnavailableException ex) {
        if (ex.disabled()) {
            return ProblemDetails.of(HttpStatus.NOT_IMPLEMENTED, "ai_provider_disabled",
                    "O copiloto de IA não está habilitado nesta instalação.");
        }
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "ai_provider_unavailable",
                "O provedor de IA não respondeu. Tente novamente em alguns instantes.");
    }

    @ExceptionHandler(AiBudgetExceededException.class)
    ProblemDetail handleBudget(AiBudgetExceededException ex) {
        var problem = ProblemDetails.of(HttpStatus.PAYMENT_REQUIRED, "ai_budget_exceeded",
                "O orçamento de IA deste mês foi esgotado.");
        problem.setProperty("monthlyLimit", ex.monthlyLimit().toPlainString());
        problem.setProperty("spent", ex.spent().toPlainString());
        return problem;
    }

    @ExceptionHandler(InvalidModelResponseException.class)
    ProblemDetail handleInvalidResponse(InvalidModelResponseException ex) {
        return ProblemDetails.of(HttpStatus.BAD_GATEWAY, "ai_response_rejected",
                "A resposta do modelo não atendeu ao formato exigido e foi recusada.");
    }

    @ExceptionHandler(StaleAiBudgetException.class)
    ProblemDetail handleStaleBudget(StaleAiBudgetException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, "ai_budget_stale",
                "O teto de gasto foi alterado por outra pessoa. Recarregue e tente novamente.");
    }
}
