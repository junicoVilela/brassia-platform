package br.com.brew.brassia.shared.web;

import br.com.brew.brassia.shared.observability.Trace;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Fábrica central de respostas de erro no formato Problem Details (RFC 9457).
 *
 * <p>Garante o mesmo formato tanto na camada MVC (retornando {@link ProblemDetail})
 * quanto em componentes fora do MVC, como os handlers de segurança (escrevendo o
 * corpo diretamente na resposta). Campos obrigatórios pelo contrato:
 * {@code type, title, status, code, traceId}.
 */
public final class ProblemDetails {

    private static final String TYPE_BASE = "https://brassia.brew.com.br/problems/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemDetails() {
    }

    /** Constrói um {@link ProblemDetail} para uso em @ExceptionHandler do MVC. */
    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setProperty("code", code);
        problem.setProperty("traceId", currentTraceId());
        return problem;
    }

    /** Escreve um corpo problem+json em respostas fora do pipeline MVC (segurança). */
    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var body = new LinkedHashMap<String, Object>();
        body.put("type", TYPE_BASE + code);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("traceId", currentTraceId());
        MAPPER.writeValue(response.getOutputStream(), body);
    }

    /** Identificador de correlação da requisição atual; nunca nulo. */
    public static String currentTraceId() {
        return Trace.currentTraceId();
    }
}
