package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import br.com.brew.brassia.shared.web.ProblemDetails;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Recusas do login federado (SEC-B07). O {@code @Order} vence o catch-all do advice global. */
@Order(0)
@RestControllerAdvice
class SecurityExceptionHandler {

    /**
     * O aperto de mão não confere, ou o provedor não está disponível.
     *
     * <p>400 e não 500: não há defeito nosso aqui. Provedor inexistente, provedor desativado, state
     * desconhecido, aperto de mão vencido ou já usado — todos são a mesma coisa do ponto de vista de quem
     * chama: a tentativa não vale.
     *
     * <p><strong>A mensagem é uma só, deliberadamente.</strong> Distinguir "este provedor não existe" de
     * "este provedor está desativado" contaria a quem sonda quais provedores a cervejaria tem configurados;
     * distinguir "vencido" de "state não bate" diria qual amarra falhou, e cada uma existe contra um ataque
     * diferente.
     *
     * <p>Isto atende o caminho de API. A volta do navegador não passa por aqui: o controller a captura e
     * redireciona para a tela de login, porque devolver um corpo JSON deixaria a pessoa numa página em
     * branco no meio de um login.
     */
    @ExceptionHandler(InvalidSsoHandshakeException.class)
    ProblemDetail handleInvalidHandshake(InvalidSsoHandshakeException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "sso_handshake_invalid",
                "Não foi possível iniciar ou concluir o login federado.");
    }
}
