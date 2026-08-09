package br.com.brew.brassia.digitaltwin.adapter.inbound.web.dto;

import br.com.brew.brassia.digitaltwin.domain.Estimate;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import br.com.brew.brassia.digitaltwin.domain.ProfileMetric;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do perfil aprendido (DTW-001). */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ComputeRequest(
            @NotNull UUID recipeId,
            @NotEmpty List<UUID> batchIds) {
    }

    /**
     * Uma estimativa como o mundo a vê.
     *
     * <p>{@code mean} nunca viaja sozinha: vai sempre acompanhada de {@code sampleSize},
     * {@code confidence} e da faixa. É o critério da história no contrato — quem consome a API não
     * consegue pegar o número sem ver sobre quantas observações ele foi construído.
     */
    public record EstimateView(
            String metric,
            String label,
            BigDecimal mean,
            BigDecimal standardDeviation,
            BigDecimal lowerBound,
            BigDecimal upperBound,
            int sampleSize,
            String confidence,
            boolean usable) {

        public static EstimateView from(ProfileMetric metric, Estimate estimate) {
            return new EstimateView(metric.name(), metric.label(), estimate.mean(),
                    estimate.standardDeviation(), estimate.lowerBound(), estimate.upperBound(),
                    estimate.sampleSize(), estimate.confidence().name(), estimate.usable());
        }
    }

    public record ProfileView(
            UUID id,
            UUID recipeId,
            int version,
            List<EstimateView> estimates,
            /** Os lotes lidos. É o que torna o número reproduzível — e a exclusão de um lote, visível. */
            List<UUID> observedBatchIds,
            Instant computedAt,
            boolean hasAnyUsableEstimate) {

        public static ProfileView from(LearnedProfile profile) {
            return new ProfileView(profile.id(), profile.recipeId(), profile.version(),
                    profile.estimates().entrySet().stream()
                            .map(e -> EstimateView.from(e.getKey(), e.getValue())).toList(),
                    profile.observedBatchIds(), profile.computedAt(), profile.hasAnyUsableEstimate());
        }

        public static List<ProfileView> from(List<LearnedProfile> profiles) {
            return profiles.stream().map(ProfileView::from).toList();
        }
    }
}
