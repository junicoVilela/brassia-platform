package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Etapa de um POP de limpeza/sanitização (CLN-001), com campos tipados da ficha:
 * método, produto, faixa autorizada (concentração e temperatura), tempo, vazão/ação
 * mecânica, EPIs, alternativa, proibição e evidência exigida. Faixas: min ≤ max.
 */
public record ProcedureStep(UUID id, int sequence, String method, String product,
        BigDecimal concentrationMinPct, BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC,
        Integer timeMinutes, String flow, String ppe, String alternative, String prohibition,
        boolean evidenceRequired) {

    public ProcedureStep {
        Objects.requireNonNull(id, "id");
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequência deve ser positiva");
        }
        method = requireText(method, "método");
        product = trimToNull(product);
        requireRange(concentrationMinPct, concentrationMaxPct, "concentração");
        requireRange(tempMinC, tempMaxC, "temperatura");
        if (timeMinutes != null && timeMinutes < 0) {
            throw new IllegalArgumentException("tempo não pode ser negativo");
        }
        flow = trimToNull(flow);
        ppe = trimToNull(ppe);
        alternative = trimToNull(alternative);
        prohibition = trimToNull(prohibition);
    }

    public static ProcedureStep of(int sequence, String method, String product, BigDecimal concentrationMinPct,
            BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC, Integer timeMinutes,
            String flow, String ppe, String alternative, String prohibition, boolean evidenceRequired) {
        return new ProcedureStep(UUID.randomUUID(), sequence, method, product, concentrationMinPct,
                concentrationMaxPct, tempMinC, tempMaxC, timeMinutes, flow, ppe, alternative, prohibition,
                evidenceRequired);
    }

    private static void requireRange(BigDecimal min, BigDecimal max, String field) {
        if (min != null && min.signum() < 0) {
            throw new IllegalArgumentException(field + " mínima não pode ser negativa");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException(field + ": mínimo não pode ser maior que o máximo");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
