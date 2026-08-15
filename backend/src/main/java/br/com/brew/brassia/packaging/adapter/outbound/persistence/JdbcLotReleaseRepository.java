package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.LotReleaseRepository;
import br.com.brew.brassia.packaging.domain.LotRelease;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLotReleaseRepository implements LotReleaseRepository {

    private final JdbcClient jdbc;

    JdbcLotReleaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LotRelease release) {
        jdbc.sql("""
                INSERT INTO packaging_finished_lot_release (finished_lot_id, brewery_id, released_by,
                                                            released_at, note)
                VALUES (:lot, :brewery, :by, :at, :note)
                """)
                .param("lot", release.finishedLotId())
                .param("brewery", release.breweryId())
                .param("by", release.releasedBy())
                .param("at", Timestamp.from(release.releasedAt()))
                .param("note", release.note())
                .update();
    }

    @Override
    public Optional<LotRelease> find(UUID breweryId, UUID finishedLotId) {
        return jdbc.sql("""
                SELECT finished_lot_id, brewery_id, released_by, released_at, note
                FROM packaging_finished_lot_release
                WHERE brewery_id = :brewery AND finished_lot_id = :lot
                """)
                .param("brewery", breweryId).param("lot", finishedLotId)
                .query((rs, row) -> new LotRelease(rs.getObject("finished_lot_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class), rs.getObject("released_by", UUID.class),
                        rs.getTimestamp("released_at").toInstant(), rs.getString("note")))
                .optional();
    }

    @Override
    public Map<UUID, LotRelease> findAll(UUID breweryId, Set<UUID> finishedLotIds) {
        if (finishedLotIds.isEmpty()) {
            // Sem isto, o IN vazio vira SQL inválido — e a lista sem lote nenhum é o caso do primeiro dia
            // de uso, que é justamente quando ninguém quer ver um erro.
            return Map.of();
        }
        var out = new HashMap<UUID, LotRelease>();
        jdbc.sql("""
                SELECT finished_lot_id, brewery_id, released_by, released_at, note
                FROM packaging_finished_lot_release
                WHERE brewery_id = :brewery AND finished_lot_id IN (:lots)
                """)
                .param("brewery", breweryId).param("lots", finishedLotIds)
                .query((rs, row) -> new LotRelease(rs.getObject("finished_lot_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class), rs.getObject("released_by", UUID.class),
                        rs.getTimestamp("released_at").toInstant(), rs.getString("note")))
                .list()
                .forEach(r -> out.put(r.finishedLotId(), r));
        return out;
    }
}
