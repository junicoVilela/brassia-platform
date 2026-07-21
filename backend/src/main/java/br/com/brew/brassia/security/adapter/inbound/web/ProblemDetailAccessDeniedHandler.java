package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Responde 403 em Problem Details quando o usuário autenticado não tem permissão. */
public final class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetails.write(response, HttpStatus.FORBIDDEN, "forbidden",
                "Você não tem permissão para esta operação.");
    }
}
