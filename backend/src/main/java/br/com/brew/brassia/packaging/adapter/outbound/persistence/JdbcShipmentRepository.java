package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.ShipmentRepository;
import br.com.brew.brassia.packaging.domain.Shipment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcShipmentRepository implements ShipmentRepository {

    private static final String COLUMNS = """
            id, brewery_id, finished_lot_id, destination, contact, units, shipped_on, note,
            recorded_by, recorded_at
            """;

    private final JdbcClient jdbc;

    JdbcShipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Shipment shipment) {
        jdbc.sql("""
                INSERT INTO packaging_shipment (id, brewery_id, finished_lot_id, destination, contact,
                        units, shipped_on, note, recorded_by, recorded_at)
                VALUES (:id, :brewery, :lot, :destination, :contact, :units, :shippedOn, :note,
                        :recordedBy, :recordedAt)
                """)
                .param("id", shipment.id())
                .param("brewery", shipment.breweryId())
                .param("lot", shipment.finishedLotId())
                .param("destination", shipment.destination())
                .param("contact", shipment.contact())
                .param("units", shipment.units())
                .param("shippedOn", shipment.shippedOn())
                .param("note", shipment.note())
                .param("recordedBy", shipment.recordedBy())
                .param("recordedAt", Timestamp.from(shipment.recordedAt()))
                .update();
    }

    @Override
    public List<Shipment> findByLot(UUID breweryId, UUID finishedLotId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id = :lot "
                        + "ORDER BY shipped_on DESC, destination")
                .param("brewery", breweryId).param("lot", finishedLotId)
                .query(JdbcShipmentRepository::map).list();
    }

    @Override
    public List<Shipment> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_shipment WHERE brewery_id = :brewery "
                        + "ORDER BY shipped_on DESC, destination")
                .param("brewery", breweryId).query(JdbcShipmentRepository::map).list();
    }

    @Override
    public List<Shipment> findByLots(UUID breweryId, List<UUID> finishedLotIds) {
        if (finishedLotIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id IN (:lots) "
                        + "ORDER BY shipped_on DESC, destination")
                .param("brewery", breweryId).param("lots", finishedLotIds)
                .query(JdbcShipmentRepository::map).list();
    }

    @Override
    public int shippedUnits(UUID breweryId, UUID finishedLotId) {
        return jdbc.sql("SELECT COALESCE(SUM(units), 0) FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id = :lot")
                .param("brewery", breweryId).param("lot", finishedLotId)
                .query(Integer.class).single();
    }

    private static Shipment map(ResultSet rs, int rowNum) throws SQLException {
        return Shipment.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("finished_lot_id", UUID.class),
                rs.getString("destination"),
                rs.getString("contact"),
                rs.getInt("units"),
                rs.getDate("shipped_on").toLocalDate(),
                rs.getString("note"),
                rs.getObject("recorded_by", UUID.class),
                rs.getTimestamp("recorded_at").toInstant());
    }
}
