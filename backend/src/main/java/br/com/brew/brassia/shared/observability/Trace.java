package br.com.brew.brassia.shared.observability;

import org.slf4j.MDC;

/**
 * Ponto único do identificador de correlação da requisição ({@code traceId}),
 * mantido no MDC do SLF4J para aparecer em logs e respostas de erro.
 */
public final class Trace {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String UNKNOWN = "unknown";

    private Trace() {
    }

    public static void put(String traceId) {
        MDC.put(TRACE_ID_MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    /** traceId da requisição atual; nunca nulo. */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : UNKNOWN;
    }
}
