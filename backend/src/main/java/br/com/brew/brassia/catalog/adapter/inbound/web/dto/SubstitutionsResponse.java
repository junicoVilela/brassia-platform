package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import br.com.brew.brassia.catalog.application.port.inbound.RankSubstitutionsUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resposta do ranking de substitutos técnicos (REC-010). */
public record SubstitutionsResponse(UUID ingredientId, String code, String name, boolean hasProfile,
        List<Match> matches) {

    public record Comparison(String property, BigDecimal target, BigDecimal candidate, String unit, boolean similar) {}

    public record Match(UUID ingredientId, String code, String name, String sourceName, BigDecimal score,
            String confidence, List<Comparison> comparisons) {}

    public static SubstitutionsResponse from(RankSubstitutionsUseCase.Result result) {
        var matches = result.matches().stream().map(SubstitutionsResponse::toMatch).toList();
        return new SubstitutionsResponse(result.targetId(), result.targetCode(), result.targetName(),
                result.hasProfile(), matches);
    }

    private static Match toMatch(RankSubstitutionsUseCase.Match m) {
        var comparisons = m.comparisons().stream()
                .map(c -> new Comparison(c.property(), c.target(), c.candidate(), c.unit(), c.similar()))
                .toList();
        return new Match(m.ingredientId(), m.code(), m.name(), m.sourceName(), m.score(), m.confidence(), comparisons);
    }
}
