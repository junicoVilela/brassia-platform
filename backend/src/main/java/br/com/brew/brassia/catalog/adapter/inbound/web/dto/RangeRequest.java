package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import br.com.brew.brassia.catalog.domain.PropertyRange;
import java.math.BigDecimal;

/** Faixa opcional de uma propriedade técnica. */
public record RangeRequest(BigDecimal min, BigDecimal max, String unit) {

    public PropertyRange toRange() {
        return new PropertyRange(min, max, unit);
    }
}
