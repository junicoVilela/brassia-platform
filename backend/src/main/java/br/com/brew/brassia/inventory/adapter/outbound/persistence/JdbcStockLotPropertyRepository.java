package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.application.port.outbound.StockLotPropertyRepository;
import br.com.brew.brassia.inventory.domain.LotPropertyConfidence;
import br.com.brew.brassia.inventory.domain.LotPropertySource;
import br.com.brew.brassia.inventory.domain.StockLotProperty;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStockLotPropertyRepository implements StockLotPropertyRepository {

    private final JdbcClient jdbc;

    JdbcStockLotPropertyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(StockLotProperty p) {
        jdbc.sql("""
                INSERT INTO stock_lot_property (
                    id, lot_id, brewery_id, property, measured_value, unit, source, confidence,
                    recorded_at, recorded_by)
                VALUES (:id, :lot, :brewery, :property, :value, :unit, :source, :confidence, :at, :by)
                """)
                .param("id", p.id())
                .param("lot", p.lotId())
                .param("brewery", p.breweryId())
                .param("property", p.property())
                .param("value", p.measuredValue())
                .param("unit", p.unit())
                .param("source", p.source().name())
                .param("confidence", p.confidence().name())
                .param("at", Timestamp.from(p.recordedAt()))
                .param("by", p.recordedBy())
                .update();
    }

    @Override
    public List<StockLotProperty> findCurrentByLot(UUID breweryId, UUID lotId) {
        // Uma linha por propriedade: a revisão mais recente (última vale).
        return jdbc.sql("""
                SELECT DISTINCT ON (property) id, lot_id, brewery_id, property, measured_value, unit, source,
                       confidence, recorded_at, recorded_by
                FROM stock_lot_property
                WHERE brewery_id = :brewery AND lot_id = :lot
                ORDER BY property, recorded_at DESC
                """)
                .param("brewery", breweryId).param("lot", lotId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public List<StockLotProperty> findHistoryByLot(UUID breweryId, UUID lotId) {
        return jdbc.sql("""
                SELECT id, lot_id, brewery_id, property, measured_value, unit, source, confidence,
                       recorded_at, recorded_by
                FROM stock_lot_property
                WHERE brewery_id = :brewery AND lot_id = :lot
                ORDER BY property, recorded_at DESC
                """)
                .param("brewery", breweryId).param("lot", lotId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private StockLotProperty map(ResultSet rs) throws SQLException {
        return StockLotProperty.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("lot_id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("property"),
                rs.getBigDecimal("measured_value"),
                rs.getString("unit"),
                LotPropertySource.valueOf(rs.getString("source")),
                LotPropertyConfidence.valueOf(rs.getString("confidence")),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getObject("recorded_by", UUID.class));
    }
}
