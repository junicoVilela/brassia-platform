package br.com.brew.brassia.referencedata.adapter.outbound.persistence;

import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import br.com.brew.brassia.referencedata.domain.DatasetStatus;
import br.com.brew.brassia.referencedata.domain.PermissionStatus;
import br.com.brew.brassia.referencedata.domain.ReferenceSourceId;
import br.com.brew.brassia.referencedata.domain.Style;
import br.com.brew.brassia.referencedata.domain.StyleAuthority;
import br.com.brew.brassia.referencedata.domain.StyleId;
import br.com.brew.brassia.referencedata.domain.StyleRange;
import br.com.brew.brassia.referencedata.domain.StyleSet;
import br.com.brew.brassia.referencedata.domain.StyleSetId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStyleSetRepository implements StyleSetRepository {

    private static final String SET_COLUMNS =
            "id, brewery_id, source_id, authority, edition, language, effective_from, effective_to, attribution, "
                    + "permission_status, status, published_at, version";

    private final JdbcClient jdbc;

    JdbcStyleSetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCoordinates(UUID breweryId, StyleAuthority authority, String edition, String language) {
        return jdbc.sql("""
                SELECT 1 FROM style_set
                WHERE authority = :authority AND edition = :edition AND language = :language
                  AND ((:brewery IS NULL AND brewery_id IS NULL) OR brewery_id = :brewery)
                """)
                .param("authority", authority.name()).param("edition", edition).param("language", language)
                .param("brewery", breweryId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(StyleSet s) {
        jdbc.sql("""
                INSERT INTO style_set
                    (id, brewery_id, source_id, authority, edition, language, effective_from, effective_to,
                     attribution, permission_status, status, published_at, version)
                VALUES (:id, :brewery, :source, :authority, :edition, :language, :from, :to, :attribution,
                        :permission, :status, :publishedAt, :version)
                """)
                .param("id", s.id().value())
                .param("brewery", s.breweryId())
                .param("source", s.sourceId().value())
                .param("authority", s.authority().name())
                .param("edition", s.edition())
                .param("language", s.language())
                .param("from", Timestamp.from(s.effectiveFrom()))
                .param("to", s.effectiveTo() == null ? null : Timestamp.from(s.effectiveTo()))
                .param("attribution", s.attribution())
                .param("permission", s.permissionStatus().name())
                .param("status", s.status().name())
                .param("publishedAt", s.publishedAt() == null ? null : Timestamp.from(s.publishedAt()))
                .param("version", s.version())
                .update();
        for (Style style : s.styles()) {
            insertStyle(s.id().value(), style);
        }
    }

    private void insertStyle(UUID setId, Style st) {
        jdbc.sql("""
                INSERT INTO style
                    (id, style_set_id, code, name, family, category,
                     og_min, og_max, og_unit, fg_min, fg_max, fg_unit, abv_min, abv_max, abv_unit,
                     ibu_min, ibu_max, ibu_unit, color_min, color_max, color_unit,
                     general_impression, detailed_profile)
                VALUES (:id, :set, :code, :name, :family, :category,
                        :ogMin, :ogMax, :ogUnit, :fgMin, :fgMax, :fgUnit, :abvMin, :abvMax, :abvUnit,
                        :ibuMin, :ibuMax, :ibuUnit, :colorMin, :colorMax, :colorUnit,
                        :impression, :profile)
                """)
                .param("id", st.id().value()).param("set", setId)
                .param("code", st.code()).param("name", st.name())
                .param("family", st.family()).param("category", st.category())
                .param("ogMin", st.og().min()).param("ogMax", st.og().max()).param("ogUnit", st.og().unit())
                .param("fgMin", st.fg().min()).param("fgMax", st.fg().max()).param("fgUnit", st.fg().unit())
                .param("abvMin", st.abv().min()).param("abvMax", st.abv().max()).param("abvUnit", st.abv().unit())
                .param("ibuMin", st.ibu().min()).param("ibuMax", st.ibu().max()).param("ibuUnit", st.ibu().unit())
                .param("colorMin", st.color().min()).param("colorMax", st.color().max())
                .param("colorUnit", st.color().unit())
                .param("impression", st.generalImpression()).param("profile", st.detailedProfile())
                .update();
    }

    @Override
    public Optional<StyleSet> findVisible(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + SET_COLUMNS + """
                 FROM style_set WHERE id = :id AND (brewery_id IS NULL OR brewery_id = :brewery)
                """)
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> mapSet(rs, loadStyles(id))).optional();
    }

    @Override
    public List<StyleSet> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("SELECT " + SET_COLUMNS + """
                 FROM style_set WHERE brewery_id IS NULL OR brewery_id = :brewery
                 ORDER BY authority, edition LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> mapSet(rs, List.of())).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM style_set WHERE brewery_id IS NULL OR brewery_id = :brewery")
                .param("brewery", breweryId).query(Long.class).single();
    }

    @Override
    public boolean markPublished(UUID breweryId, UUID id, Instant publishedAt, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE style_set
                SET status = 'PUBLISHED', published_at = :at, version = :newVersion, updated_at = now()
                WHERE id = :id AND brewery_id = :brewery AND version = :expected AND status = 'DRAFT'
                """)
                .param("at", Timestamp.from(publishedAt)).param("newVersion", expectedVersion + 1)
                .param("brewery", breweryId).param("id", id).param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    private List<Style> loadStyles(UUID setId) {
        return jdbc.sql("""
                SELECT id, code, name, family, category,
                       og_min, og_max, og_unit, fg_min, fg_max, fg_unit, abv_min, abv_max, abv_unit,
                       ibu_min, ibu_max, ibu_unit, color_min, color_max, color_unit,
                       general_impression, detailed_profile
                FROM style WHERE style_set_id = :set ORDER BY code
                """)
                .param("set", setId)
                .query((rs, n) -> mapStyle(rs)).list();
    }

    private static StyleSet mapSet(ResultSet rs, List<Style> styles) throws SQLException {
        return StyleSet.reconstitute(
                new StyleSetId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                new ReferenceSourceId(rs.getObject("source_id", UUID.class)),
                StyleAuthority.valueOf(rs.getString("authority")),
                rs.getString("edition"),
                rs.getString("language"),
                instant(rs, "effective_from"),
                instant(rs, "effective_to"),
                rs.getString("attribution"),
                PermissionStatus.valueOf(rs.getString("permission_status")),
                DatasetStatus.valueOf(rs.getString("status")),
                instant(rs, "published_at"),
                styles,
                rs.getLong("version"));
    }

    private static Style mapStyle(ResultSet rs) throws SQLException {
        return Style.reconstitute(
                new StyleId(rs.getObject("id", UUID.class)),
                rs.getString("code"), rs.getString("name"), rs.getString("family"), rs.getString("category"),
                range(rs, "og_min", "og_max", "og_unit"),
                range(rs, "fg_min", "fg_max", "fg_unit"),
                range(rs, "abv_min", "abv_max", "abv_unit"),
                range(rs, "ibu_min", "ibu_max", "ibu_unit"),
                range(rs, "color_min", "color_max", "color_unit"),
                rs.getString("general_impression"), rs.getString("detailed_profile"));
    }

    private static StyleRange range(ResultSet rs, String minCol, String maxCol, String unitCol) throws SQLException {
        BigDecimal min = rs.getBigDecimal(minCol);
        BigDecimal max = rs.getBigDecimal(maxCol);
        String unit = rs.getString(unitCol);
        if (min == null && max == null && unit == null) {
            return StyleRange.none();
        }
        return new StyleRange(min, max, unit);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
