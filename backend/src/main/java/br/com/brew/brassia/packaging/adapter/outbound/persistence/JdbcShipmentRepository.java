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
            reversed_at, reversed_by, reversal_reason,
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
    public java.util.Optional<Shipment> findForUpdate(UUID breweryId, UUID shipmentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND id = :id FOR UPDATE")
                .param("brewery", breweryId).param("id", shipmentId)
                .query(JdbcShipmentRepository::map).optional();
    }

    @Override
    public void updateReversal(Shipment shipment) {
        var reversal = shipment.reversal().orElseThrow();
        jdbc.sql("""
                UPDATE packaging_shipment
                SET reversed_at = :at, reversed_by = :by, reversal_reason = :reason
                WHERE id = :id AND brewery_id = :brewery AND reversed_at IS NULL
                """)
                .param("at", Timestamp.from(reversal.at())).param("by", reversal.by())
                .param("reason", reversal.reason())
                .param("id", shipment.id()).param("brewery", shipment.breweryId())
                .update();
    }

    @Override
    public List<Shipment> findByLots(UUID breweryId, List<UUID> finishedLotIds) {
        if (finishedLotIds.isEmpty()) {
            return List.of();
        }
        // Estornadas ficam de fora: o recall que comunica um destino estornado avisa quem nunca recebeu.
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id IN (:lots) "
                        + "AND reversed_at IS NULL "
                        + "ORDER BY shipped_on DESC, destination")
                .param("brewery", breweryId).param("lots", finishedLotIds)
                .query(JdbcShipmentRepository::map).list();
    }

    @Override
    public int shippedUnits(UUID breweryId, UUID finishedLotId) {
        // Estornada não conta: a saída líquida é o que o recall persegue, e o saldo sem destino do lote
        // precisa voltar a mostrar a cerveja que a expedição errada tinha escondido.
        return jdbc.sql("SELECT COALESCE(SUM(units), 0) FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id = :lot AND reversed_at IS NULL")
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
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getTimestamp("reversed_at") == null ? null
                        : new Shipment.Reversal(rs.getObject("reversed_by", UUID.class),
                                rs.getString("reversal_reason"),
                                rs.getTimestamp("reversed_at").toInstant()));
    }
}
