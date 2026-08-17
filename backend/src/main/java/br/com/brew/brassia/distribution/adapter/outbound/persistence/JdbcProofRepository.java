package br.com.brew.brassia.distribution.adapter.outbound.persistence;

import br.com.brew.brassia.distribution.application.port.outbound.ProofRepository;
import br.com.brew.brassia.distribution.domain.CoarseLocation;
import br.com.brew.brassia.distribution.domain.ConsentedMedia;
import br.com.brew.brassia.distribution.domain.DeliveryOutcome;
import br.com.brew.brassia.distribution.domain.ProofOfDelivery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProofRepository implements ProofRepository {

    /**
     * O escopo entra por subconsulta até a carga.
     *
     * <p>A prova não guarda {@code brewery_id} próprio: duplicá-lo criaria duas verdades sobre de quem é
     * a entrega, e a segunda envelheceria.
     */
    private static final String SCOPE = """
             AND stop_id IN (SELECT s.id FROM distribution_load_stop s
                             JOIN distribution_load l ON l.id = s.load_id
                             WHERE l.brewery_id = :brewery)
            """;

    private final JdbcClient jdbc;

    JdbcProofRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UUID breweryId, ProofOfDelivery p) {
        var media = p.media().orElse(null);
        var lugar = p.location().orElse(null);
        jdbc.sql("""
                INSERT INTO distribution_proof (id, stop_id, outcome, occurred_at, recorded_by, note,
                        outside_window, media_kind, media_key, media_consented_by, media_consented_at,
                        media_purpose, latitude, longitude, corrects_proof_id)
                VALUES (:id, :stop, :outcome, :at, :by, :note, :outside, :mediaKind, :mediaKey,
                        :consentedBy, :consentedAt, :purpose, :lat, :lon, :corrects)
                """)
                .param("id", p.id()).param("stop", p.stopId()).param("outcome", p.outcome().name())
                .param("at", Timestamp.from(p.occurredAt())).param("by", p.recordedBy())
                .param("note", p.note().orElse(null)).param("outside", p.outsideWindow())
                .param("mediaKind", media == null ? null : media.kind().name())
                .param("mediaKey", media == null ? null : media.storageKey())
                .param("consentedBy", media == null ? null : media.consentedByName())
                .param("consentedAt", media == null ? null : Timestamp.from(media.consentedAt()))
                .param("purpose", media == null ? null : media.purpose())
                .param("lat", lugar == null ? null : lugar.latitude())
                .param("lon", lugar == null ? null : lugar.longitude())
                .param("corrects", p.correctsProofId().orElse(null))
                .update();

        gravaItens(p.id(), p.deliveredContainerIds(), "DELIVERED");
        gravaItens(p.id(), p.collectedContainerIds(), "COLLECTED");
    }

    private void gravaItens(UUID proofId, List<UUID> containers, String direction) {
        for (var containerId : containers) {
            jdbc.sql("""
                    INSERT INTO distribution_proof_item (id, proof_id, container_id, direction)
                    VALUES (:id, :proof, :container, :direction)
                    """)
                    .param("id", UUID.randomUUID()).param("proof", proofId)
                    .param("container", containerId).param("direction", direction)
                    .update();
        }
    }

    @Override
    public Optional<ProofOfDelivery> originalOf(UUID breweryId, UUID stopId) {
        return jdbc.sql(select() + " WHERE stop_id = :stop AND corrects_proof_id IS NULL" + SCOPE)
                .param("stop", stopId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public List<ProofOfDelivery> ofStop(UUID breweryId, UUID stopId) {
        return jdbc.sql(select() + " WHERE stop_id = :stop" + SCOPE + " ORDER BY recorded_at")
                .param("stop", stopId).param("brewery", breweryId)
                .query(this::map).list();
    }

    @Override
    public List<ProofOfDelivery> ofLoad(UUID breweryId, UUID loadId) {
        return jdbc.sql(select() + """
                 WHERE stop_id IN (SELECT id FROM distribution_load_stop WHERE load_id = :load)
                """ + SCOPE + " ORDER BY recorded_at")
                .param("load", loadId).param("brewery", breweryId)
                .query(this::map).list();
    }

    private static String select() {
        return """
                SELECT id, stop_id, outcome, occurred_at, recorded_by, note, outside_window,
                       media_kind, media_key, media_consented_by, media_consented_at, media_purpose,
                       latitude, longitude, corrects_proof_id
                FROM distribution_proof
                """;
    }

    private ProofOfDelivery map(ResultSet rs, int row) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var entregues = new ArrayList<UUID>();
        var recolhidos = new ArrayList<UUID>();
        jdbc.sql("SELECT container_id, direction FROM distribution_proof_item WHERE proof_id = :p")
                .param("p", id)
                .query((r, i) -> {
                    var containerId = r.getObject("container_id", UUID.class);
                    if ("DELIVERED".equals(r.getString("direction"))) {
                        entregues.add(containerId);
                    } else {
                        recolhidos.add(containerId);
                    }
                    return containerId;
                })
                .list();

        var mediaKind = rs.getString("media_kind");
        var media = mediaKind == null ? null
                : new ConsentedMedia(ConsentedMedia.MediaKind.valueOf(mediaKind),
                        rs.getString("media_key"), rs.getString("media_consented_by"),
                        rs.getTimestamp("media_consented_at").toInstant(),
                        rs.getString("media_purpose"));
        var lat = rs.getBigDecimal("latitude");
        var lugar = lat == null ? null : new CoarseLocation(lat, rs.getBigDecimal("longitude"));
        var corrige = rs.getObject("corrects_proof_id", UUID.class);

        if (corrige == null) {
            return ProofOfDelivery.record(id, rs.getObject("stop_id", UUID.class),
                    DeliveryOutcome.valueOf(rs.getString("outcome")),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getObject("recorded_by", UUID.class), entregues, recolhidos,
                    rs.getString("note"), media, lugar, rs.getBoolean("outside_window"));
        }
        // A correção volta do banco como correção: reconstituí-la como original perderia justamente o
        // elo que a torna auditável.
        return ProofOfDelivery.reconstituteCorrection(id, rs.getObject("stop_id", UUID.class), corrige,
                DeliveryOutcome.valueOf(rs.getString("outcome")),
                rs.getTimestamp("occurred_at").toInstant(), rs.getObject("recorded_by", UUID.class),
                entregues, recolhidos, rs.getString("note"), rs.getBoolean("outside_window"));
    }
}
