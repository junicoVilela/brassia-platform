package br.com.brew.brassia.shared.web;

import br.com.brew.brassia.shared.observability.Trace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga ou gera um {@code traceId} por requisição, disponibilizando-o no MDC
 * (para logs) e no cabeçalho de resposta. Registrado antes do Spring Security
 * para que também as respostas de 401/403 carreguem o identificador.
 */
public final class RequestTraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        Trace.put(traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            Trace.clear();
        }
    }
}
