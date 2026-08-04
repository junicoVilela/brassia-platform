package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.application.port.outbound.CapaPolicyRepository;
import br.com.brew.brassia.quality.domain.CapaPolicy;
import br.com.brew.brassia.quality.domain.Severity;
import java.util.EnumMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Uma linha por severidade; severidade sem linha volta a exigir prazo informado. */
@Repository
class JdbcCapaPolicyRepository implements CapaPolicyRepository {

    private final JdbcClient jdbc;

    JdbcCapaPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CapaPolicy find(UUID breweryId) {
        var bySeverity = new EnumMap<Severity, CapaPolicy.Deadlines>(Severity.class);
        jdbc.sql("""
                SELECT severity, containment_days, investigation_days, verification_days
                FROM quality_capa_policy WHERE brewery_id = :brewery
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> bySeverity.put(Severity.valueOf(rs.getString("severity")),
                        new CapaPolicy.Deadlines(rs.getInt("containment_days"),
                                rs.getInt("investigation_days"), rs.getInt("verification_days"))))
                .list();
        return CapaPolicy.reconstitute(breweryId, bySeverity);
    }

    @Override
    public void save(CapaPolicy policy) {
        jdbc.sql("DELETE FROM quality_capa_policy WHERE brewery_id = :brewery")
                .param("brewery", policy.breweryId()).update();
        policy.bySeverity().forEach((severity, d) -> jdbc.sql("""
                INSERT INTO quality_capa_policy (brewery_id, severity, containment_days,
                    investigation_days, verification_days)
                VALUES (:brewery, :severity, :containment, :investigation, :verification)
                """)
                .param("brewery", policy.breweryId()).param("severity", severity.name())
                .param("containment", d.containmentDays()).param("investigation", d.investigationDays())
                .param("verification", d.verificationDays())
                .update());
    }
}
