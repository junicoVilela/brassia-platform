package br.com.brew.brassia.digitaltwin.adapter.outbound.persistence;

import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.domain.Confidence;
import br.com.brew.brassia.digitaltwin.domain.Estimate;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import br.com.brew.brassia.digitaltwin.domain.ProfileMetric;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Perfis aprendidos em PostgreSQL (DTW-001).
 *
 * <p><strong>Não há {@code UPDATE} nem {@code DELETE}, e é a regra.</strong> Um perfil é evidência do que
 * se sabia num instante; recalcular produz versão nova. Alterar um perfil gravado faria as decisões
 * tomadas sobre ele parecerem tomadas sobre outros números.
 */
@Repository
class JdbcLearnedProfileRepository implements LearnedProfileRepository {

    private final JdbcClient jdbc;

    JdbcLearnedProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LearnedProfile profile) {
        jdbc.sql("""
                INSERT INTO twin_profile (id, brewery_id, recipe_id, version, computed_by, computed_at)
                VALUES (:id, :brewery, :recipe, :version, :by, :at)
                """)
                .param("id", profile.id())
                .param("brewery", profile.breweryId())
                .param("recipe", profile.recipeId())
                .param("version", profile.version())
                .param("by", profile.computedBy())
                .param("at", Timestamp.from(profile.computedAt()))
                .update();

        for (var entry : profile.estimates().entrySet()) {
            var estimate = entry.getValue();
            jdbc.sql("""
                    INSERT INTO twin_profile_estimate (profile_id, metric, mean, standard_deviation,
                            lower_bound, upper_bound, sample_size, confidence)
                    VALUES (:profile, :metric, :mean, :deviation, :lower, :upper, :sample, :confidence)
                    """)
                    .param("profile", profile.id())
                    .param("metric", entry.getKey().name())
                    .param("mean", estimate.mean())
                    .param("deviation", estimate.standardDeviation())
                    .param("lower", estimate.lowerBound())
                    .param("upper", estimate.upperBound())
                    .param("sample", estimate.sampleSize())
                    .param("confidence", estimate.confidence().name())
                    .update();
        }

        for (var batchId : profile.observedBatchIds()) {
            jdbc.sql("INSERT INTO twin_profile_sample (profile_id, batch_id) VALUES (:profile, :batch)")
                    .param("profile", profile.id()).param("batch", batchId)
                    .update();
        }
    }

    @Override
    public int highestVersionOf(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT COALESCE(MAX(version), 0) FROM twin_profile
                WHERE brewery_id = :brewery AND recipe_id = :recipe
                """)
                .param("brewery", breweryId).param("recipe", recipeId)
                .query(Integer.class).single();
    }

    @Override
    public Optional<LearnedProfile> latestOf(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT id, brewery_id, recipe_id, version, computed_by, computed_at
                FROM twin_profile
                WHERE brewery_id = :brewery AND recipe_id = :recipe
                ORDER BY version DESC LIMIT 1
                """)
                .param("brewery", breweryId).param("recipe", recipeId)
                .query(this::map).optional();
    }

    @Override
    public List<LearnedProfile> historyOf(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT id, brewery_id, recipe_id, version, computed_by, computed_at
                FROM twin_profile
                WHERE brewery_id = :brewery AND recipe_id = :recipe
                ORDER BY version DESC
                """)
                .param("brewery", breweryId).param("recipe", recipeId)
                .query(this::map).list();
    }

    private LearnedProfile map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        return LearnedProfile.reconstitute(
                id,
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("recipe_id", UUID.class),
                rs.getInt("version"),
                estimatesOf(id),
                sampleOf(id),
                rs.getObject("computed_by", UUID.class),
                rs.getTimestamp("computed_at").toInstant());
    }

    /**
     * As estimativas gravadas.
     *
     * <p>Métrica desconhecida na coluna é <strong>ignorada</strong>, não fatal: é o cenário de rollback —
     * uma versão futura acrescenta uma grandeza, alguém calcula um perfil com ela, e a aplicação volta para
     * esta versão. Explodir aqui derrubaria a leitura do perfil inteiro, e com ela as métricas que esta
     * versão conhece perfeitamente.
     */
    private Map<ProfileMetric, Estimate> estimatesOf(UUID profileId) {
        var estimates = new EnumMap<ProfileMetric, Estimate>(ProfileMetric.class);
        jdbc.sql("""
                SELECT metric, mean, standard_deviation, lower_bound, upper_bound, sample_size, confidence
                FROM twin_profile_estimate WHERE profile_id = :profile
                """)
                .param("profile", profileId)
                .query((rs, n) -> new Row(
                        rs.getString("metric"), rs.getBigDecimal("mean"),
                        rs.getBigDecimal("standard_deviation"), rs.getBigDecimal("lower_bound"),
                        rs.getBigDecimal("upper_bound"), rs.getInt("sample_size"),
                        rs.getString("confidence")))
                .list()
                .forEach(row -> row.resolvedMetric().ifPresent(metric -> estimates.put(metric, row.toEstimate())));
        return estimates;
    }

    /** Uma linha crua da tabela de estimativas, para separar a leitura da conversão. */
    private record Row(String metric, java.math.BigDecimal mean, java.math.BigDecimal deviation,
            java.math.BigDecimal lower, java.math.BigDecimal upper, int sampleSize, String confidence) {

        /** Nome diferente do acessor do record de propósito: um resolve, o outro só devolve o texto. */
        Optional<ProfileMetric> resolvedMetric() {
            try {
                return Optional.of(ProfileMetric.valueOf(metric));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        Estimate toEstimate() {
            return new Estimate(mean, deviation, lower, upper, sampleSize,
                    Confidence.valueOf(confidence));
        }
    }

    private List<UUID> sampleOf(UUID profileId) {
        return new ArrayList<>(jdbc.sql(
                "SELECT batch_id FROM twin_profile_sample WHERE profile_id = :profile ORDER BY batch_id")
                .param("profile", profileId)
                .query(UUID.class).list());
    }
}
