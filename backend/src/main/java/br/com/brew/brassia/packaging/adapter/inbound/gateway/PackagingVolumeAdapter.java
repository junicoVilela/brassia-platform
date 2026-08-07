package br.com.brew.brassia.packaging.adapter.inbound.gateway;

import br.com.brew.brassia.utilities.PackagedVolumeSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Os litros envasados no período (UTL-001): o denominador do indicador de utilidades.
 *
 * <p>Soma o volume das execuções de envase, não o dos planos: plano é intenção, e dividir consumo
 * real por volume planejado daria um indicador que melhora quando a fábrica planeja demais.
 */
@Component
class PackagingVolumeAdapter implements PackagedVolumeSource {

    private final JdbcClient jdbc;

    PackagingVolumeAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal packagedLitersIn(UUID breweryId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(packaged_volume_liters), 0) FROM packaging_run
                WHERE brewery_id = :brewery AND executed_at >= :from AND executed_at < :to
                """)
                .param("brewery", breweryId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query(BigDecimal.class).single();
    }
}
