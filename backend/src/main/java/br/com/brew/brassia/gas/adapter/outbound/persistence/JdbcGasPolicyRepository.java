package br.com.brew.brassia.gas.adapter.outbound.persistence;

import br.com.brew.brassia.gas.application.port.outbound.GasPolicyRepository;
import br.com.brew.brassia.gas.domain.GasPolicy;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGasPolicyRepository implements GasPolicyRepository {

    private final JdbcClient jdbc;

    JdbcGasPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public GasPolicy find(UUID breweryId) {
        return jdbc.sql("SELECT brewery_id, requalification_months, version FROM gas_policy "
                        + "WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query((rs, n) -> GasPolicy.reconstitute(rs.getObject("brewery_id", UUID.class),
                        rs.getObject("requalification_months", Integer.class), rs.getLong("version")))
                .optional()
                .orElseGet(() -> GasPolicy.none(breweryId));
    }

    @Override
    public void save(GasPolicy policy) {
        jdbc.sql("""
                INSERT INTO gas_policy (brewery_id, requalification_months, version)
                VALUES (:brewery, :months, 0)
                ON CONFLICT (brewery_id) DO UPDATE
                SET requalification_months = :months, version = gas_policy.version + 1
                """)
                .param("brewery", policy.breweryId())
                .param("months", policy.requalificationMonths().orElse(null))
                .update();
    }
}
