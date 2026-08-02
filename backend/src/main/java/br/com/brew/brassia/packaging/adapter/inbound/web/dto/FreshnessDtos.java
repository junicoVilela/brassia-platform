package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.FreshnessRecord;
import br.com.brew.brassia.packaging.domain.ShelfLifePolicy;
import br.com.brew.brassia.packaging.domain.ShelfLifeRecommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos de oxigênio e vida útil (FSL-001). */
public final class FreshnessDtos {

    private FreshnessDtos() {
    }

    public record RecordFreshnessRequest(
            @NotNull @PositiveOrZero BigDecimal dissolvedOxygenPpb,
            @NotNull @PositiveOrZero BigDecimal totalPackageOxygenPpb,
            @NotBlank @Size(max = 120) String purgeMethod,
            boolean purgeVerified,
            @NotBlank @Size(max = 120) String sealCheckMethod,
            boolean sealCheckPassed) {}

    /** O motivo é obrigatório: é ele que explica uma data que a evidência não sustentava. */
    public record OverrideShelfLifeRequest(
            @Positive int shelfLifeDays,
            @NotBlank @Size(max = 200) String reason) {}

    public record ShelfLifePolicyRequest(
            @NotEmpty @Valid List<TierRequest> tiers,
            @Positive int fallbackDays) {

        public record TierRequest(@NotNull @Positive BigDecimal maxTpoPpb, @Positive int shelfLifeDays) {}

        public ShelfLifePolicy toPolicy() {
            return new ShelfLifePolicy(
                    tiers.stream().map(t -> new ShelfLifePolicy.Tier(t.maxTpoPpb(), t.shelfLifeDays())).toList(),
                    fallbackDays);
        }
    }

    public record ShelfLifePolicyView(List<TierView> tiers, int fallbackDays) {

        public record TierView(BigDecimal maxTpoPpb, int shelfLifeDays) {}

        public static ShelfLifePolicyView from(ShelfLifePolicy policy) {
            return new ShelfLifePolicyView(
                    policy.tiers().stream().map(t -> new TierView(t.maxTpoPpb(), t.shelfLifeDays())).toList(),
                    policy.fallbackDays());
        }
    }

    /** Recomendação explicada: qual faixa pegou, e o que reduz a confiança nela. */
    public record RecommendationView(int shelfLifeDays, LocalDate bestBefore, BigDecimal totalPackageOxygenPpb,
            BigDecimal matchedTierMaxTpoPpb, boolean withinPolicyTiers, List<FactorView> factors,
            List<String> caveats) {

        public record FactorView(String name, boolean trustworthy, String explanation) {}

        public static RecommendationView from(ShelfLifeRecommendation r) {
            return new RecommendationView(r.shelfLifeDays(), r.bestBefore(), r.totalPackageOxygenPpb(),
                    r.matchedTierMaxTpoPpb(), r.withinPolicyTiers(),
                    r.factors().stream()
                            .map(f -> new FactorView(f.name(), f.trustworthy(), f.explanation()))
                            .toList(),
                    r.caveats());
        }
    }

    /** O recomendado e o sobreposto lado a lado: a evidência nunca é apagada pelo override. */
    public record FreshnessView(LocalDate packagedOn, BigDecimal dissolvedOxygenPpb,
            BigDecimal totalPackageOxygenPpb, BigDecimal headspaceOxygenPpb, String purgeMethod,
            boolean purgeVerified, String sealCheckMethod, boolean sealCheckPassed, boolean evidenceComplete,
            Integer recommendedShelfLifeDays, LocalDate recommendedBestBefore, Integer overrideShelfLifeDays,
            LocalDate overrideBestBefore, String overrideReason, UUID overriddenBy, Instant overriddenAt,
            boolean extendsBeyondRecommendation, Integer effectiveShelfLifeDays, LocalDate effectiveBestBefore) {

        public static FreshnessView from(FreshnessRecord r) {
            var m = r.measurement();
            return new FreshnessView(r.packagedOn(), m.dissolvedOxygenPpb(), m.totalPackageOxygenPpb(),
                    m.headspaceOxygenPpb(), m.purgeMethod(), m.purgeVerified(), m.sealCheckMethod(),
                    m.sealCheckPassed(), m.evidenceComplete(), r.recommendedShelfLifeDays(),
                    r.recommendedBestBefore(), r.overrideShelfLifeDays(), r.overrideBestBefore(),
                    r.overrideReason(), r.overriddenBy(), r.overriddenAt(), r.extendsBeyondRecommendation(),
                    r.effectiveShelfLifeDays(), r.effectiveBestBefore());
        }
    }

    /** Resposta do registro: o que ficou gravado e a recomendação explicada que saiu dele. */
    public record RecordedView(FreshnessView freshness, RecommendationView recommendation) {

        public static RecordedView from(FreshnessRecord record, ShelfLifeRecommendation recommendation) {
            return new RecordedView(FreshnessView.from(record),
                    recommendation == null ? null : RecommendationView.from(recommendation));
        }
    }
}
