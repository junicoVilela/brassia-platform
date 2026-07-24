package br.com.brew.brassia.referencedata.adapter.outbound.persistence;

import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.LicenseInfo;
import br.com.brew.brassia.referencedata.domain.PermissionStatus;
import br.com.brew.brassia.referencedata.domain.ReferenceSource;
import br.com.brew.brassia.referencedata.domain.ReferenceSourceId;
import br.com.brew.brassia.referencedata.domain.SourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReferenceSourceRepository implements ReferenceSourceRepository {

    private static final String COLUMNS =
            "id, brewery_id, type, name, owner, url, license_name, permission_status, attribution, "
                    + "review_frequency, responsible, version";

    private final JdbcClient jdbc;

    JdbcReferenceSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByName(UUID breweryId, String name) {
        return jdbc.sql("""
                SELECT 1 FROM reference_source
                WHERE lower(name) = lower(:name)
                  AND ((:brewery IS NULL AND brewery_id IS NULL) OR brewery_id = :brewery)
                """)
                .param("name", name).param("brewery", breweryId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(ReferenceSource s) {
        jdbc.sql("""
                INSERT INTO reference_source
                    (id, brewery_id, type, name, owner, url, license_name, permission_status, attribution,
                     review_frequency, responsible, version)
                VALUES (:id, :brewery, :type, :name, :owner, :url, :license, :permission, :attribution,
                        :reviewFrequency, :responsible, :version)
                """)
                .param("id", s.id().value())
                .param("brewery", s.breweryId())
                .param("type", s.type().name())
                .param("name", s.name())
                .param("owner", s.owner())
                .param("url", s.url())
                .param("license", s.license().licenseName())
                .param("permission", s.permissionStatus().name())
                .param("attribution", s.license().attribution())
                .param("reviewFrequency", s.reviewFrequency())
                .param("responsible", s.responsible())
                .param("version", s.version())
                .update();
    }

    @Override
    public Optional<ReferenceSource> findVisible(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM reference_source
                 WHERE id = :id AND (brewery_id IS NULL OR brewery_id = :brewery)
                """)
                .param("id", id).param("brewery", breweryId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<ReferenceSource> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM reference_source
                 WHERE brewery_id IS NULL OR brewery_id = :brewery
                 ORDER BY name LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("""
                SELECT count(*) FROM reference_source
                WHERE brewery_id IS NULL OR brewery_id = :brewery
                """)
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    private static ReferenceSource map(ResultSet rs) throws SQLException {
        var license = new LicenseInfo(
                rs.getString("license_name"),
                PermissionStatus.valueOf(rs.getString("permission_status")),
                rs.getString("attribution"));
        return ReferenceSource.reconstitute(
                new ReferenceSourceId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                SourceType.valueOf(rs.getString("type")),
                rs.getString("name"),
                rs.getString("owner"),
                rs.getString("url"),
                license,
                rs.getString("review_frequency"),
                rs.getString("responsible"),
                rs.getLong("version"));
    }
}
