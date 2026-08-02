package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.CarbonationRepository;
import br.com.brew.brassia.packaging.domain.Carbonation;
import br.com.brew.brassia.packaging.domain.CarbonationMethod;
import br.com.brew.brassia.packaging.domain.PrimingSugar;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCarbonationRepository implements CarbonationRepository {

    /** Alertas são texto livre curto; guardados numa linha só, separados por quebra de linha. */
    private static final String ALERT_SEPARATOR = "\n";

    private final JdbcClient jdbc;

    JdbcCarbonationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Carbonation c) {
        // Recalcular substitui a decisão inteira: um plano tem uma carbonatação vigente.
        jdbc.sql("""
                INSERT INTO packaging_carbonation (plan_id, brewery_id, method, target_volumes,
                    reference_temp_c, residual_volumes, priming_sugar, priming_sugar_grams, pressure_bar,
                    calculation_method, calculator_version, alerts, confirmed_by, confirmed_at, version)
                VALUES (:plan, :brewery, :method, :target, :temp, :residual, :sugar, :grams, :pressure,
                    :calcMethod, :calcVersion, :alerts, :by, :at, 0)
                ON CONFLICT (plan_id) DO UPDATE SET
                    method = EXCLUDED.method,
                    target_volumes = EXCLUDED.target_volumes,
                    reference_temp_c = EXCLUDED.reference_temp_c,
                    residual_volumes = EXCLUDED.residual_volumes,
                    priming_sugar = EXCLUDED.priming_sugar,
                    priming_sugar_grams = EXCLUDED.priming_sugar_grams,
                    pressure_bar = EXCLUDED.pressure_bar,
                    calculation_method = EXCLUDED.calculation_method,
                    calculator_version = EXCLUDED.calculator_version,
                    alerts = EXCLUDED.alerts,
                    confirmed_by = EXCLUDED.confirmed_by,
                    confirmed_at = EXCLUDED.confirmed_at,
                    version = packaging_carbonation.version + 1
                """)
                .param("plan", c.planId())
                .param("brewery", c.breweryId())
                .param("method", c.method().name())
                .param("target", c.targetVolumes())
                .param("temp", c.referenceTempC())
                .param("residual", c.residualVolumes())
                .param("sugar", c.primingSugar() == null ? null : c.primingSugar().name())
                .param("grams", c.primingSugarGrams())
                .param("pressure", c.pressureBar())
                .param("calcMethod", c.calculationMethod())
                .param("calcVersion", c.calculatorVersion())
                .param("alerts", c.alerts().isEmpty() ? null : String.join(ALERT_SEPARATOR, c.alerts()))
                .param("by", c.confirmedBy())
                .param("at", Timestamp.from(c.confirmedAt()))
                .update();
    }

    @Override
    public Optional<Carbonation> findByPlan(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT plan_id, brewery_id, method, target_volumes, reference_temp_c, residual_volumes,
                       priming_sugar, priming_sugar_grams, pressure_bar, calculation_method, calculator_version,
                       alerts, confirmed_by, confirmed_at, version
                FROM packaging_carbonation
                WHERE brewery_id = :brewery AND plan_id = :plan
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private Carbonation map(ResultSet rs) throws SQLException {
        var sugar = rs.getString("priming_sugar");
        return Carbonation.reconstitute(
                rs.getObject("plan_id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                CarbonationMethod.valueOf(rs.getString("method")),
                rs.getBigDecimal("target_volumes"),
                rs.getBigDecimal("reference_temp_c"),
                rs.getBigDecimal("residual_volumes"),
                sugar == null ? null : PrimingSugar.valueOf(sugar),
                rs.getBigDecimal("priming_sugar_grams"),
                rs.getBigDecimal("pressure_bar"),
                rs.getString("calculation_method"),
                rs.getString("calculator_version"),
                alerts(rs.getString("alerts")),
                rs.getObject("confirmed_by", UUID.class),
                rs.getTimestamp("confirmed_at").toInstant(),
                rs.getLong("version"));
    }

    private static List<String> alerts(String raw) {
        return raw == null || raw.isBlank() ? List.of() : Arrays.asList(raw.split(ALERT_SEPARATOR));
    }
}
