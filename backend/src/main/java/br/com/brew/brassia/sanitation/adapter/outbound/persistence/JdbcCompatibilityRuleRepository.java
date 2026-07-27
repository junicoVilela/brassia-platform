package br.com.brew.brassia.sanitation.adapter.outbound.persistence;

import br.com.brew.brassia.sanitation.application.port.outbound.CompatibilityRuleRepository;
import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import br.com.brew.brassia.sanitation.domain.EquipmentMaterial;
import br.com.brew.brassia.sanitation.domain.RiskLevel;
import br.com.brew.brassia.sanitation.domain.SoilingLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompatibilityRuleRepository implements CompatibilityRuleRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, material, soiling, risk, previous_product, procedure_code, method, alternative,
                   restriction
            FROM sanitation_compatibility_rule
            """;

    private final JdbcClient jdbc;

    JdbcCompatibilityRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CompatibilityRule r) {
        jdbc.sql("""
                INSERT INTO sanitation_compatibility_rule (
                    id, brewery_id, material, soiling, risk, previous_product, procedure_code, method, alternative,
                    restriction)
                VALUES (:id, :brewery, :material, :soiling, :risk, :previous, :code, :method, :alt, :restriction)
                """)
                .param("id", r.id())
                .param("brewery", r.breweryId())
                .param("material", r.material().name())
                .param("soiling", r.soiling().name())
                .param("risk", r.risk().name())
                .param("previous", r.previousProduct())
                .param("code", r.procedureCode())
                .param("method", r.method())
                .param("alt", r.alternative())
                .param("restriction", r.restriction())
                .update();
    }

    @Override
    public boolean existsKey(UUID breweryId, EquipmentMaterial material, SoilingLevel soiling, RiskLevel risk,
            String previousProduct) {
        return jdbc.sql("""
                SELECT 1 FROM sanitation_compatibility_rule
                WHERE brewery_id = :brewery AND material = :material AND soiling = :soiling AND risk = :risk
                  AND COALESCE(previous_product, '') = COALESCE(:previous, '')
                LIMIT 1
                """)
                .param("brewery", breweryId).param("material", material.name())
                .param("soiling", soiling.name()).param("risk", risk.name()).param("previous", previousProduct)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public List<CompatibilityRule> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY material, soiling, risk")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public List<CompatibilityRule> findCandidates(UUID breweryId, EquipmentMaterial material, SoilingLevel soiling,
            RiskLevel risk) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND material = :material AND soiling = :soiling "
                        + "AND risk = :risk")
                .param("brewery", breweryId).param("material", material.name())
                .param("soiling", soiling.name()).param("risk", risk.name())
                .query((rs, n) -> map(rs))
                .list();
    }

    private CompatibilityRule map(ResultSet rs) throws SQLException {
        return CompatibilityRule.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                EquipmentMaterial.valueOf(rs.getString("material")),
                SoilingLevel.valueOf(rs.getString("soiling")),
                RiskLevel.valueOf(rs.getString("risk")),
                rs.getString("previous_product"),
                rs.getString("procedure_code"),
                rs.getString("method"),
                rs.getString("alternative"),
                rs.getString("restriction"));
    }
}
