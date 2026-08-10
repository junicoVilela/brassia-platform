package br.com.brew.brassia.sensory.adapter.outbound.persistence;

import br.com.brew.brassia.sensory.application.port.outbound.DescriptorRepository;
import br.com.brew.brassia.sensory.domain.DescriptorCategory;
import br.com.brew.brassia.sensory.domain.DescriptorSource;
import br.com.brew.brassia.sensory.domain.Hypothesis;
import br.com.brew.brassia.sensory.domain.LicenseTier;
import br.com.brew.brassia.sensory.domain.SensoryDescriptor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Descritores em PostgreSQL (SEN-002). */
@Repository
class JdbcDescriptorRepository implements DescriptorRepository {

    private final JdbcClient jdbc;

    JdbcDescriptorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SensoryDescriptor d) {
        jdbc.sql("""
                INSERT INTO sensory_descriptor (id, brewery_id, code, name, category, source_name,
                        source_reference, license_tier, source_attribution, perception_threshold,
                        threshold_unit, created_by)
                VALUES (:id, :brewery, :code, :name, :category, :sourceName, :sourceRef, :tier,
                        :attribution, :threshold, :unit, :by)
                """)
                .param("id", d.id()).param("brewery", d.breweryId())
                .param("code", d.code()).param("name", d.name())
                .param("category", d.category().name())
                .param("sourceName", d.source().name())
                .param("sourceRef", d.source().referenceText().orElse(null))
                .param("tier", d.source().tier().name())
                .param("attribution", d.source().attributionText().orElse(null))
                .param("threshold", d.perceptionThreshold().orElse(null))
                .param("unit", d.thresholdUnit().orElse(null))
                .param("by", d.breweryId())
                .update();

        for (var term : d.synonyms()) {
            jdbc.sql("""
                    INSERT INTO sensory_descriptor_synonym (descriptor_id, term, normalized_term)
                    VALUES (:id, :term, :normalized)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("id", d.id()).param("term", term).param("normalized", normalize(term))
                    .update();
        }
        for (var h : d.hypotheses()) {
            jdbc.sql("""
                    INSERT INTO sensory_descriptor_hypothesis (descriptor_id, possible_cause,
                            suggested_check, likelihood)
                    VALUES (:id, :cause, :check, :likelihood)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("id", d.id()).param("cause", h.possibleCause())
                    .param("check", h.suggestedCheck()).param("likelihood", h.likelihood().name())
                    .update();
        }
    }

    @Override
    public Optional<SensoryDescriptor> find(UUID breweryId, UUID descriptorId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", descriptorId)
                .query(this::map).optional();
    }

    @Override
    public List<SensoryDescriptor> list(UUID breweryId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery ORDER BY category, name")
                .param("brewery", breweryId)
                .query(this::map).list();
    }

    /**
     * Busca por termo em código, nome e sinônimos, tudo normalizado.
     *
     * <p>A normalização acontece dos DOIS lados — no que está gravado e no que foi digitado. Normalizar só
     * na consulta faria "Cartonádo" não achar "cartonado" gravado com acento, que é justamente o caso
     * comum de quem digita rápido na mesa de prova.
     */
    @Override
    public List<SensoryDescriptor> searchByTerm(UUID breweryId, String term) {
        var normalized = normalize(term);
        return jdbc.sql(SELECT + """
                 WHERE brewery_id = :brewery
                   AND (lower(code) = :term
                        OR :term = ANY (SELECT normalized_term FROM sensory_descriptor_synonym s
                                        WHERE s.descriptor_id = sensory_descriptor.id)
                        OR translate(lower(name), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')
                           LIKE '%' || :term || '%')
                 ORDER BY category, name
                """)
                .param("brewery", breweryId).param("term", normalized)
                .query(this::map).list();
    }

    @Override
    public List<StyleLink> byStyle(UUID breweryId, String styleCode) {
        return jdbc.sql("""
                SELECT d.*, l.expected
                FROM sensory_descriptor d
                JOIN sensory_style_descriptor l ON l.descriptor_id = d.id AND l.brewery_id = d.brewery_id
                WHERE d.brewery_id = :brewery AND l.style_code = :style
                ORDER BY l.expected DESC, d.name
                """)
                .param("brewery", breweryId).param("style", styleCode)
                .query((rs, n) -> new StyleLink(map(rs, n), rs.getBoolean("expected")))
                .list();
    }

    @Override
    public void linkToStyle(UUID breweryId, String styleCode, UUID descriptorId, boolean expected) {
        jdbc.sql("""
                INSERT INTO sensory_style_descriptor (brewery_id, style_code, descriptor_id, expected)
                VALUES (:brewery, :style, :descriptor, :expected)
                ON CONFLICT (brewery_id, style_code, descriptor_id)
                DO UPDATE SET expected = EXCLUDED.expected
                """)
                .param("brewery", breweryId).param("style", styleCode)
                .param("descriptor", descriptorId).param("expected", expected)
                .update();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, code, name, category, source_name, source_reference, license_tier,
                   source_attribution, perception_threshold, threshold_unit
            FROM sensory_descriptor
            """;

    private SensoryDescriptor map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var source = new DescriptorSource(rs.getString("source_name"), rs.getString("source_reference"),
                LicenseTier.valueOf(rs.getString("license_tier")), rs.getString("source_attribution"));
        return SensoryDescriptor.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"), rs.getString("name"),
                DescriptorCategory.valueOf(rs.getString("category")),
                synonymsOf(id), source,
                rs.getBigDecimal("perception_threshold"), rs.getString("threshold_unit"),
                hypothesesOf(id));
    }

    private Set<String> synonymsOf(UUID descriptorId) {
        return new LinkedHashSet<>(jdbc.sql(
                "SELECT term FROM sensory_descriptor_synonym WHERE descriptor_id = :id ORDER BY term")
                .param("id", descriptorId).query(String.class).list());
    }

    private List<Hypothesis> hypothesesOf(UUID descriptorId) {
        return jdbc.sql("""
                SELECT possible_cause, suggested_check, likelihood
                FROM sensory_descriptor_hypothesis WHERE descriptor_id = :id ORDER BY likelihood
                """)
                .param("id", descriptorId)
                .query((rs, n) -> new Hypothesis(rs.getString("possible_cause"),
                        rs.getString("suggested_check"),
                        Hypothesis.Likelihood.valueOf(rs.getString("likelihood"))))
                .list();
    }

    /** Sem acento e em minúsculas — a mesma normalização do domínio, aplicada na gravação e na busca. */
    private static String normalize(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
