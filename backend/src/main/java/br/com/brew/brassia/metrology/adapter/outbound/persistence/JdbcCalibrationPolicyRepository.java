package br.com.brew.brassia.metrology.adapter.outbound.persistence;

import br.com.brew.brassia.metrology.application.port.outbound.CalibrationPolicyRepository;
import br.com.brew.brassia.metrology.domain.CalibrationPolicy;
import br.com.brew.brassia.metrology.domain.InstrumentType;
import java.util.EnumMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A política é um conjunto de linhas, uma por tipo. Regravar apaga e reinsere: tipo sem linha
 * significa "sem periodicidade", e é assim que se volta ao vencimento vindo do certificado.
 */
@Repository
class JdbcCalibrationPolicyRepository implements CalibrationPolicyRepository {

    private final JdbcClient jdbc;

    JdbcCalibrationPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CalibrationPolicy find(UUID breweryId) {
        var months = new EnumMap<InstrumentType, Integer>(InstrumentType.class);
        jdbc.sql("SELECT instrument_type, months FROM metrology_calibration_policy "
                        + "WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query((rs, n) -> months.put(InstrumentType.valueOf(rs.getString("instrument_type")),
                        rs.getInt("months")))
                .list();
        return CalibrationPolicy.reconstitute(breweryId, months);
    }

    @Override
    public void save(CalibrationPolicy policy) {
        jdbc.sql("DELETE FROM metrology_calibration_policy WHERE brewery_id = :brewery")
                .param("brewery", policy.breweryId()).update();
        policy.monthsByType().forEach((type, months) -> jdbc.sql("""
                INSERT INTO metrology_calibration_policy (brewery_id, instrument_type, months)
                VALUES (:brewery, :type, :months)
                """)
                .param("brewery", policy.breweryId()).param("type", type.name()).param("months", months)
                .update());
    }
}
