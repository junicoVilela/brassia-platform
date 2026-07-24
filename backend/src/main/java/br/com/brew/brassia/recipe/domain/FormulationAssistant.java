package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assistente de formulação (REC-009): compara metas com faixas alvo (de um estilo
 * ou perfil personalizado) e produz sugestões determinísticas explicando o
 * impacto previsto. Apenas orienta — não substitui ingrediente nem altera receita.
 */
public final class FormulationAssistant {

    private static final Map<String, String> SUGGESTIONS = Map.ofEntries(
            Map.entry("OG_BELOW", "OG abaixo da faixa: aumentar malte-base ou a eficiência de mostura eleva a gravidade."),
            Map.entry("OG_ABOVE", "OG acima da faixa: reduzir malte-base ou aumentar o volume baixa a gravidade."),
            Map.entry("FG_BELOW", "FG abaixo da faixa: levedura menos atenuante ou mostura mais alta eleva a FG."),
            Map.entry("FG_ABOVE", "FG acima da faixa: levedura mais atenuante ou mostura mais baixa reduz a FG."),
            Map.entry("ABV_BELOW", "ABV abaixo da faixa: elevar OG ou a atenuação aumenta o teor alcoólico."),
            Map.entry("ABV_ABOVE", "ABV acima da faixa: reduzir a OG baixa o teor alcoólico."),
            Map.entry("IBU_BELOW", "IBU abaixo da faixa: aumentar lúpulo de amargor ou o tempo de fervura eleva o IBU."),
            Map.entry("IBU_ABOVE", "IBU acima da faixa: reduzir lúpulo de amargor ou o tempo de fervura baixa o IBU."),
            Map.entry("COLOR_BELOW", "Cor abaixo da faixa: adicionar malte mais escuro/torrado eleva a cor."),
            Map.entry("COLOR_ABOVE", "Cor acima da faixa: reduzir maltes escuros/torrados baixa a cor."));

    public List<AttributeGuidance> assess(Map<String, BigDecimal> targets, Map<String, AttributeRange> ranges) {
        var result = new ArrayList<AttributeGuidance>();
        if (targets == null) {
            return result;
        }
        targets.forEach((attribute, value) -> {
            if (value == null) {
                return;
            }
            AttributeRange range = ranges == null ? null : ranges.get(attribute);
            if (range == null || range.isEmpty()) {
                result.add(new AttributeGuidance(attribute, value, null, null, null, GuidanceStatus.NO_RANGE, null));
                return;
            }
            GuidanceStatus status = classify(value, range);
            result.add(new AttributeGuidance(attribute, value, range.min(), range.max(), range.unit(), status,
                    suggestion(attribute, status)));
        });
        return result;
    }

    private static GuidanceStatus classify(BigDecimal value, AttributeRange range) {
        if (range.min() != null && value.compareTo(range.min()) < 0) {
            return GuidanceStatus.BELOW;
        }
        if (range.max() != null && value.compareTo(range.max()) > 0) {
            return GuidanceStatus.ABOVE;
        }
        return GuidanceStatus.WITHIN;
    }

    private static String suggestion(String attribute, GuidanceStatus status) {
        if (status == GuidanceStatus.WITHIN || status == GuidanceStatus.NO_RANGE) {
            return null;
        }
        return SUGGESTIONS.get(attribute.toUpperCase(Locale.ROOT) + "_" + status.name());
    }
}
