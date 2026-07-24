package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.domain.StyleRange;
import java.math.BigDecimal;

/** Faixa opcional de um parâmetro de estilo. Ausente (null) vira faixa vazia. */
public record RangeRequest(BigDecimal min, BigDecimal max, String unit) {

    public StyleRange toRange() {
        if (min == null && max == null && (unit == null || unit.isBlank())) {
            return StyleRange.none();
        }
        return new StyleRange(min, max, unit);
    }

    public static StyleRange orNone(RangeRequest request) {
        return request == null ? StyleRange.none() : request.toRange();
    }
}
