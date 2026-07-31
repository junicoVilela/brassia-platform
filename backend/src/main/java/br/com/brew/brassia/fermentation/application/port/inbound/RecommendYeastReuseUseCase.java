package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import br.com.brew.brassia.fermentation.domain.YeastRecommendation;
import java.util.List;
import java.util.UUID;

/**
 * Recomenda coletas para reutilização (YST-002). É consulta e explicável: recomendar não usa
 * nada — o pitch continua exigindo confirmação humana e lote vinculado.
 */
public interface RecommendYeastReuseUseCase {
    /** {@code strainId} nulo considera todas as cepas disponíveis. */
    Result handle(UUID breweryId, UUID strainId);

    record Result(List<YeastRecommendation> recommendations, YeastPolicy policy) {}
}
