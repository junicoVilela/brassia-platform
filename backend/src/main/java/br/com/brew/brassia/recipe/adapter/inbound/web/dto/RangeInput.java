package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.domain.AttributeRange;
import java.math.BigDecimal;

/** Faixa alvo de um atributo (estilo oficial ou perfil personalizado). */
public record RangeInput(BigDecimal min, BigDecimal max, String unit) {

    public AttributeRange toRange() {
        return new AttributeRange(min, max, unit);
    }
}
