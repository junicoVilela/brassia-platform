package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.application.port.inbound.RecommendYeastReuseUseCase;
import java.util.List;

/** Recomendações ordenadas + a política aplicada, para o resultado ser conferível. */
public record YeastReuseView(YeastPolicyDto policy, List<YeastRecommendationView> recommendations) {

    public static YeastReuseView from(RecommendYeastReuseUseCase.Result result) {
        return new YeastReuseView(
                YeastPolicyDto.from(result.policy()),
                result.recommendations().stream().map(YeastRecommendationView::from).toList());
    }
}
