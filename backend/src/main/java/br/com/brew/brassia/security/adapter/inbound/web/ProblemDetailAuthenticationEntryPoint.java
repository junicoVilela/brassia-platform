package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Responde 401 em Problem Details quando falta autenticação. */
public final class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ProblemDetails.write(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Autenticação é necessária.");
    }
}
