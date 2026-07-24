package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import br.com.brew.brassia.recipe.domain.AttributeRange;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pedido ao assistente: metas por atributo e faixas alvo (de um estilo escolhido
 * pelo frontend ou de um perfil personalizado). Nada é aplicado à receita.
 */
public record AssistRequest(Map<String, BigDecimal> targets, Map<String, RangeInput> ranges) {

    public Map<String, BigDecimal> targetsOrEmpty() {
        return targets == null ? Map.of() : targets;
    }

    public Map<String, AttributeRange> toRanges() {
        var result = new LinkedHashMap<String, AttributeRange>();
        if (ranges != null) {
            ranges.forEach((key, range) -> {
                if (range != null) {
                    result.put(key, range.toRange());
                }
            });
        }
        return result;
    }
}
