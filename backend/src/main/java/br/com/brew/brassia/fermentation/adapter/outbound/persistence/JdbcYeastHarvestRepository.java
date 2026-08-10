package br.com.brew.brassia.fermentation.adapter.outbound.persistence;

import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import br.com.brew.brassia.fermentation.domain.YeastHarvestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcYeastHarvestRepository implements YeastHarvestRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, strain_id, source_batch_id, parent_harvest_id, generation, harvested_at,
                   viability_percent, condition, storage_location, storage_temp_c, status, review_note,
                   reviewed_at, reviewed_by, pitched_batch_id, pitched_at
            FROM fermentation_yeast_harvest
            """;

    private final JdbcClient jdbc;

    JdbcYeastHarvestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(YeastHarvest h) {
        jdbc.sql("""
                INSERT INTO fermentation_yeast_harvest (id, brewery_id, code, strain_id, source_batch_id,
                    parent_harvest_id, generation, harvested_at, viability_percent, condition, storage_location,
                    storage_temp_c, status)
                VALUES (:id, :brewery, :code, :strain, :batch, :parent, :generation, :at, :viability, :condition,
                    :location, :temp, :status)
                """)
                .param("id", h.id())
                .param("brewery", h.breweryId())
                .param("code", h.code())
                .param("strain", h.strainId())
                .param("batch", h.sourceBatchId())
                .param("parent", h.parentHarvestId())
                .param("generation", h.generation())
                .param("at", Timestamp.from(h.harvestedAt()))
                .param("viability", h.viabilityPercent())
                .param("condition", h.condition())
                .param("location", h.storageLocation())
                .param("temp", h.storageTempC())
                .param("status", h.status().name())
                .update();
    }

    @Override
    public void updateReview(YeastHarvest h) {
        // Guardado pelo estado: a revisão é terminal, então só sai de quarentena.
        jdbc.sql("""
                UPDATE fermentation_yeast_harvest
                SET status = :status, review_note = :note, reviewed_at = :at, reviewed_by = :by
                WHERE id = :id AND brewery_id = :brewery AND status = 'QUARANTINE'
                """)
                .param("status", h.status().name())
                .param("note", h.reviewNote())
                .param("at", h.reviewedAt() == null ? null : Timestamp.from(h.reviewedAt()))
                .param("by", h.reviewedBy())
                .param("id", h.id())
                .param("brewery", h.breweryId())
                .update();
    }

    @Override
    public void updatePitch(YeastHarvest h) {
        // Guardado pelo estado: só coleta aprovada é consumida, então o mesmo pitch não repete.
        jdbc.sql("""
                UPDATE fermentation_yeast_harvest
                SET status = :status, pitched_batch_id = :batch, pitched_at = :at
                WHERE id = :id AND brewery_id = :brewery AND status = 'APPROVED'
                """)
                .param("status", h.status().name())
                .param("batch", h.pitchedBatchId())
                .param("at", h.pitchedAt() == null ? null : Timestamp.from(h.pitchedAt()))
                .param("id", h.id())
                .param("brewery", h.breweryId())
                .update();
    }

    @Override
    public Optional<YeastHarvest> findById(UUID breweryId, UUID harvestId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", harvestId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public Optional<YeastHarvest> findPitchedInto(UUID breweryId, UUID batchId) {
        return jdbc.sql(COLUMNS + """
                 WHERE brewery_id = :brewery AND pitched_batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM fermentation_yeast_harvest WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public List<YeastHarvest> findAll(UUID breweryId, boolean onlyAvailable) {
        var sql = COLUMNS + " WHERE brewery_id = :brewery"
                + (onlyAvailable ? " AND status = 'APPROVED'" : "") + " ORDER BY harvested_at DESC";
        return jdbc.sql(sql).param("brewery", breweryId).query((rs, n) -> map(rs)).list();
    }

    @Override
    public List<YeastHarvest> findAncestry(UUID breweryId, UUID harvestId) {
        // Sobe a linhagem até a levedura comprada (parent nulo), da mais nova para a mais antiga.
        return jdbc.sql("""
                WITH RECURSIVE ancestry AS (
                    SELECT h.*, 0 AS depth
                    FROM fermentation_yeast_harvest h
                    WHERE h.brewery_id = :brewery AND h.id = :id
                    UNION ALL
                    SELECT parent.*, a.depth + 1
                    FROM fermentation_yeast_harvest parent
                    JOIN ancestry a ON parent.id = a.parent_harvest_id
                    WHERE parent.brewery_id = :brewery
                )
                SELECT id, brewery_id, code, strain_id, source_batch_id, parent_harvest_id, generation, harvested_at,
                       viability_percent, condition, storage_location, storage_temp_c, status, review_note,
                       reviewed_at, reviewed_by, pitched_batch_id, pitched_at
                FROM ancestry ORDER BY depth
                """)
                .param("brewery", breweryId).param("id", harvestId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private YeastHarvest map(ResultSet rs) throws SQLException {
        var reviewedAt = rs.getTimestamp("reviewed_at");
        var pitchedAt = rs.getTimestamp("pitched_at");
        return YeastHarvest.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getObject("strain_id", UUID.class),
                rs.getObject("source_batch_id", UUID.class),
                rs.getObject("parent_harvest_id", UUID.class),
                rs.getInt("generation"),
                rs.getTimestamp("harvested_at").toInstant(),
                rs.getBigDecimal("viability_percent"),
                rs.getString("condition"),
                rs.getString("storage_location"),
                rs.getBigDecimal("storage_temp_c"),
                YeastHarvestStatus.valueOf(rs.getString("status")),
                rs.getString("review_note"),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getObject("reviewed_by", UUID.class),
                rs.getObject("pitched_batch_id", UUID.class),
                pitchedAt == null ? null : pitchedAt.toInstant());
    }
}
