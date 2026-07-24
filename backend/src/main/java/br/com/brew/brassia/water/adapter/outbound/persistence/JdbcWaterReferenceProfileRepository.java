package br.com.brew.brassia.water.adapter.outbound.persistence;

import br.com.brew.brassia.water.application.port.outbound.WaterReferenceProfileRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.ReferenceProfileStatus;
import br.com.brew.brassia.water.domain.WaterReferenceProfile;
import br.com.brew.brassia.water.domain.WaterReferenceProfileId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWaterReferenceProfileRepository implements WaterReferenceProfileRepository {

    private static final String COLUMNS =
            "id, brewery_id, name, region, edition, calcium, magnesium, sodium, sulfate, chloride, bicarbonate, "
                    + "alkalinity, hardness, ph, source_id, source_name, status, version";

    private final JdbcClient jdbc;

    JdbcWaterReferenceProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByNameEdition(UUID breweryId, String name, String edition) {
        return jdbc.sql("""
                SELECT 1 FROM water_reference_profile
                WHERE lower(name) = lower(:name) AND edition = :edition
                  AND ((:brewery IS NULL AND brewery_id IS NULL) OR brewery_id = :brewery)
                """)
                .param("name", name).param("edition", edition).param("brewery", breweryId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(WaterReferenceProfile p) {
        jdbc.sql("""
                INSERT INTO water_reference_profile
                    (id, brewery_id, name, region, edition, calcium, magnesium, sodium, sulfate, chloride,
                     bicarbonate, alkalinity, hardness, ph, source_id, source_name, status, version)
                VALUES (:id, :brewery, :name, :region, :edition, :ca, :mg, :na, :so4, :cl, :hco3, :alk, :hard, :ph,
                        :sourceId, :sourceName, :status, :version)
                """)
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("name", p.name())
                .param("region", p.region())
                .param("edition", p.edition())
                .param("ca", p.ions().calcium())
                .param("mg", p.ions().magnesium())
                .param("na", p.ions().sodium())
                .param("so4", p.ions().sulfate())
                .param("cl", p.ions().chloride())
                .param("hco3", p.ions().bicarbonate())
                .param("alk", p.alkalinity())
                .param("hard", p.hardness())
                .param("ph", p.ph())
                .param("sourceId", p.sourceId())
                .param("sourceName", p.sourceName())
                .param("status", p.status().name())
                .param("version", p.version())
                .update();
    }

    @Override
    public Optional<WaterReferenceProfile> findVisible(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM water_reference_profile WHERE id = :id AND (brewery_id IS NULL OR brewery_id = :brewery)
                """)
                .param("id", id).param("brewery", breweryId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<WaterReferenceProfile> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM water_reference_profile WHERE brewery_id IS NULL OR brewery_id = :brewery
                 ORDER BY name, edition LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM water_reference_profile WHERE brewery_id IS NULL OR brewery_id = :brewery")
                .param("brewery", breweryId).query(Long.class).single();
    }

    @Override
    public boolean markPublished(UUID id, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE water_reference_profile
                SET status = 'PUBLISHED', version = :newVersion, updated_at = now()
                WHERE id = :id AND version = :expected AND status = 'DRAFT'
                """)
                .param("newVersion", expectedVersion + 1).param("id", id).param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    private static WaterReferenceProfile map(ResultSet rs) throws SQLException {
        var ions = new IonProfile(rs.getBigDecimal("calcium"), rs.getBigDecimal("magnesium"),
                rs.getBigDecimal("sodium"), rs.getBigDecimal("sulfate"), rs.getBigDecimal("chloride"),
                rs.getBigDecimal("bicarbonate"));
        return WaterReferenceProfile.reconstitute(
                new WaterReferenceProfileId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("name"), rs.getString("region"), rs.getString("edition"), ions,
                rs.getBigDecimal("alkalinity"), rs.getBigDecimal("hardness"), rs.getBigDecimal("ph"),
                rs.getObject("source_id", UUID.class), rs.getString("source_name"),
                ReferenceProfileStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }
}
