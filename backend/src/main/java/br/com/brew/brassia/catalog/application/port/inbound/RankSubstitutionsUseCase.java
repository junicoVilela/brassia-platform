package br.com.brew.brassia.catalog.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranqueia substitutos técnicos de um ingrediente (REC-010). Determinístico: usa as
 * propriedades técnicas configuradas por tipo; a IA não calcula score nem inventa dados.
 */
public interface RankSubstitutionsUseCase {

    Optional<Result> handle(Query query);

    record Query(UUID breweryId, UUID ingredientId, int limit) {}

    record Comparison(String property, BigDecimal target, BigDecimal candidate, String unit, boolean similar) {}

    record Match(UUID ingredientId, String code, String name, String sourceName, BigDecimal score,
            String confidence, List<Comparison> comparisons) {}

    /**
     * @param hasProfile falso quando o ingrediente-alvo não tem perfil técnico publicado;
     *     nesse caso {@code matches} vem vazio e não há base para equivalência técnica.
     */
    record Result(UUID targetId, String targetCode, String targetName, boolean hasProfile, List<Match> matches) {}
}
