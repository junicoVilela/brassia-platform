package br.com.brew.brassia.container.adapter.outbound.persistence;

import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.domain.ContainerFill;
import br.com.brew.brassia.container.domain.ContainerLocation;
import br.com.brew.brassia.container.domain.FillNotAllowedException;
import br.com.brew.brassia.container.domain.LocationKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFillRepository implements FillRepository {

    /**
     * O escopo de cervejaria entra por subconsulta na tabela do contêiner.
     *
     * <p>O enchimento não guarda {@code brewery_id} próprio de propósito: duplicá-lo criaria duas
     * verdades sobre de quem é o vasilhame, e a segunda envelheceria.
     */
    private static final String SCOPE =
            " AND container_id IN (SELECT id FROM container WHERE brewery_id = :brewery)";

    private final JdbcClient jdbc;

    JdbcFillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(ContainerFill fill) {
        try {
            jdbc.sql("""
                    INSERT INTO container_fill (id, container_id, finished_lot_id, lot_code,
                                                volume_liters, filled_at, filled_by)
                    VALUES (:id, :container, :lot, :code, :volume, :at, :by)
                    """)
                    .param("id", fill.id()).param("container", fill.containerId())
                    .param("lot", fill.finishedLotId()).param("code", fill.lotCode())
                    .param("volume", fill.volumeLiters())
                    .param("at", Timestamp.from(fill.filledAt())).param("by", fill.filledBy())
                    .update();
        } catch (DuplicateKeyException jaCheio) {
            // O índice parcial é a garantia: duas telas enchendo o mesmo keg ao mesmo tempo passariam
            // por qualquer checagem prévia.
            throw FillNotAllowedException.alreadyFull(fill.lotCode());
        }
    }

    @Override
    public void empty(UUID breweryId, UUID containerId, Instant at) {
        jdbc.sql("UPDATE container_fill SET emptied_at = :at WHERE emptied_at IS NULL" + SCOPE
                        + " AND container_id = :container")
                .param("at", Timestamp.from(at)).param("container", containerId)
                .param("brewery", breweryId)
                .update();
    }

    @Override
    public Optional<ContainerFill> currentOf(UUID breweryId, UUID containerId) {
        return jdbc.sql(select() + " WHERE emptied_at IS NULL AND container_id = :container" + SCOPE)
                .param("container", containerId).param("brewery", breweryId)
                .query(JdbcFillRepository::map).optional();
    }

    @Override
    public List<ContainerFill> historyOf(UUID breweryId, UUID containerId) {
        return jdbc.sql(select() + " WHERE container_id = :container" + SCOPE
                        + " ORDER BY filled_at DESC")
                .param("container", containerId).param("brewery", breweryId)
                .query(JdbcFillRepository::map).list();
    }

    @Override
    public List<ContainerFill> ofLot(UUID breweryId, UUID finishedLotId) {
        return jdbc.sql(select() + " WHERE finished_lot_id = :lot" + SCOPE + " ORDER BY filled_at")
                .param("lot", finishedLotId).param("brewery", breweryId)
                .query(JdbcFillRepository::map).list();
    }

    @Override
    public void locate(ContainerLocation location) {
        jdbc.sql("""
                INSERT INTO container_location (id, container_id, kind, place, recorded_at)
                VALUES (:id, :container, :kind, :place, :at)
                """)
                .param("id", location.id()).param("container", location.containerId())
                .param("kind", location.kind().name()).param("place", location.place())
                .param("at", Timestamp.from(location.recordedAt()))
                .update();
    }

    @Override
    public List<ContainerLocation> locationsOf(UUID breweryId, UUID containerId) {
        return jdbc.sql("""
                SELECT id, container_id, kind, place, recorded_at FROM container_location
                WHERE container_id = :container
                """ + SCOPE + " ORDER BY recorded_at DESC")
                .param("container", containerId).param("brewery", breweryId)
                .query((rs, row) -> new ContainerLocation(rs.getObject("id", UUID.class),
                        rs.getObject("container_id", UUID.class),
                        LocationKind.valueOf(rs.getString("kind")), rs.getString("place"),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }

    private static String select() {
        return """
                SELECT id, container_id, finished_lot_id, lot_code, volume_liters, filled_at, filled_by,
                       emptied_at
                FROM container_fill
                """;
    }

    private static ContainerFill map(ResultSet rs, int row) throws SQLException {
        var emptied = rs.getTimestamp("emptied_at");
        return ContainerFill.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("container_id", UUID.class),
                rs.getObject("finished_lot_id", UUID.class), rs.getString("lot_code"),
                rs.getBigDecimal("volume_liters"), rs.getTimestamp("filled_at").toInstant(),
                rs.getObject("filled_by", UUID.class),
                emptied == null ? null : emptied.toInstant());
    }
}
