package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.YeastRecommendation;
import java.util.List;

/**
 * Recomendação de reutilização (YST-002). Devolve a coleta, o veredito e os fatores com suas
 * explicações — a recomendação precisa ser conferível, não uma nota opaca.
 */
public record YeastRecommendationView(
        YeastHarvestView harvest,
        boolean recommended,
        long ageDays,
        List<FactorView> factors,
        List<String> blockers) {

    public record FactorView(String name, boolean withinPolicy, String explanation) {

        static FactorView from(YeastRecommendation.Factor f) {
            return new FactorView(f.name(), f.withinPolicy(), f.explanation());
        }
    }

    public static YeastRecommendationView from(YeastRecommendation r) {
        return new YeastRecommendationView(
                YeastHarvestView.from(r.harvest()),
                r.recommended(),
                r.ageDays(),
                r.factors().stream().map(FactorView::from).toList(),
                r.blockers());
    }
}
