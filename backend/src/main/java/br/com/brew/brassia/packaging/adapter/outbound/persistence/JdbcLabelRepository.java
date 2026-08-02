package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.LabelRepository;
import br.com.brew.brassia.packaging.domain.LabelField;
import br.com.brew.brassia.packaging.domain.LabelPrint;
import br.com.brew.brassia.packaging.domain.LabelRegulatoryRule;
import br.com.brew.brassia.packaging.domain.LabelTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLabelRepository implements LabelRepository {

    /** Campos são um enum curto e ordenado; uma coluna de texto separada por vírgula basta. */
    private static final String SEPARATOR = ",";

    private static final String TEMPLATE_COLUMNS = """
            SELECT id, brewery_id, code, name, version, fields, note, created_by, created_at
            FROM packaging_label_template
            """;

    private final JdbcClient jdbc;

    JdbcLabelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertTemplate(LabelTemplate t) {
        jdbc.sql("""
                INSERT INTO packaging_label_template (id, brewery_id, code, name, version, fields, note,
                    created_by, created_at)
                VALUES (:id, :brewery, :code, :name, :version, :fields, :note, :by, :at)
                """)
                .param("id", t.id())
                .param("brewery", t.breweryId())
                .param("code", t.code())
                .param("name", t.name())
                .param("version", t.version())
                .param("fields", t.fields().stream().map(Enum::name).collect(Collectors.joining(SEPARATOR)))
                .param("note", t.note())
                .param("by", t.createdBy())
                .param("at", Timestamp.from(t.createdAt()))
                .update();
    }

    @Override
    public Optional<LabelTemplate> findTemplate(UUID breweryId, UUID templateId) {
        return jdbc.sql(TEMPLATE_COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", templateId)
                .query((rs, n) -> mapTemplate(rs))
                .optional();
    }

    @Override
    public Optional<LabelTemplate> findLatestTemplate(UUID breweryId, String code) {
        return jdbc.sql(TEMPLATE_COLUMNS
                        + " WHERE brewery_id = :brewery AND code = :code ORDER BY version DESC LIMIT 1")
                .param("brewery", breweryId).param("code", code)
                .query((rs, n) -> mapTemplate(rs))
                .optional();
    }

    @Override
    public List<LabelTemplate> findLatestTemplates(UUID breweryId) {
        // Uma linha por código: a versão vigente. O histórico segue acessível por id.
        return jdbc.sql("""
                SELECT DISTINCT ON (code) id, brewery_id, code, name, version, fields, note, created_by,
                       created_at
                FROM packaging_label_template
                WHERE brewery_id = :brewery
                ORDER BY code, version DESC
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> mapTemplate(rs))
                .list();
    }

    @Override
    public List<LabelTemplate> findTemplateVersions(UUID breweryId, String code) {
        return jdbc.sql(TEMPLATE_COLUMNS
                        + " WHERE brewery_id = :brewery AND code = :code ORDER BY version DESC")
                .param("brewery", breweryId).param("code", code)
                .query((rs, n) -> mapTemplate(rs))
                .list();
    }

    @Override
    public Optional<LabelRegulatoryRule> findRule(UUID breweryId) {
        return jdbc.sql("SELECT required_fields FROM packaging_label_rule WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(String.class)
                .optional()
                .map(raw -> new LabelRegulatoryRule(fields(raw)));
    }

    @Override
    public void saveRule(UUID breweryId, LabelRegulatoryRule rule) {
        jdbc.sql("""
                INSERT INTO packaging_label_rule (brewery_id, required_fields)
                VALUES (:brewery, :fields)
                ON CONFLICT (brewery_id) DO UPDATE SET required_fields = EXCLUDED.required_fields
                """)
                .param("brewery", breweryId)
                .param("fields", rule.requiredFields().stream().map(Enum::name)
                        .collect(Collectors.joining(SEPARATOR)))
                .update();
    }

    @Override
    public void insertPrint(LabelPrint p) {
        jdbc.sql("""
                INSERT INTO packaging_label_print (id, plan_id, brewery_id, template_id, template_code,
                    template_version, quantity, reprint, reason, printed_by, printed_at)
                VALUES (:id, :plan, :brewery, :template, :code, :version, :quantity, :reprint, :reason,
                    :by, :at)
                """)
                .param("id", p.id())
                .param("plan", p.planId())
                .param("brewery", p.breweryId())
                .param("template", p.templateId())
                .param("code", p.templateCode())
                .param("version", p.templateVersion())
                .param("quantity", p.quantity())
                .param("reprint", p.reprint())
                .param("reason", p.reason())
                .param("by", p.printedBy())
                .param("at", Timestamp.from(p.printedAt()))
                .update();
    }

    @Override
    public List<LabelPrint> findPrints(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT id, plan_id, brewery_id, template_id, template_code, template_version, quantity,
                       reprint, reason, printed_by, printed_at
                FROM packaging_label_print
                WHERE brewery_id = :brewery AND plan_id = :plan
                ORDER BY printed_at DESC
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> LabelPrint.reconstitute(
                        rs.getObject("id", UUID.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("template_id", UUID.class),
                        rs.getString("template_code"),
                        rs.getInt("template_version"),
                        rs.getInt("quantity"),
                        rs.getBoolean("reprint"),
                        rs.getString("reason"),
                        rs.getObject("printed_by", UUID.class),
                        rs.getTimestamp("printed_at").toInstant()))
                .list();
    }

    @Override
    public boolean hasPrint(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT 1 FROM packaging_label_print
                WHERE brewery_id = :brewery AND plan_id = :plan LIMIT 1
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private LabelTemplate mapTemplate(ResultSet rs) throws SQLException {
        return new LabelTemplate(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("version"),
                // Lista, não conjunto: no template a ordem dos campos é o próprio layout.
                orderedFields(rs.getString("fields")),
                rs.getString("note"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private static List<LabelField> orderedFields(String raw) {
        return Arrays.stream(raw.split(SEPARATOR))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LabelField::valueOf)
                .toList();
    }

    /** Na regra a ordem não importa: o que vale é o conjunto de campos exigidos. */
    private static EnumSet<LabelField> fields(String raw) {
        var parsed = orderedFields(raw);
        return parsed.isEmpty() ? EnumSet.noneOf(LabelField.class) : EnumSet.copyOf(parsed);
    }
}
