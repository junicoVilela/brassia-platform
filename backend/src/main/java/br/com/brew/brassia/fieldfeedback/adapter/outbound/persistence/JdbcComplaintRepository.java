package br.com.brew.brassia.fieldfeedback.adapter.outbound.persistence;

import br.com.brew.brassia.fieldfeedback.application.port.outbound.ComplaintRepository;
import br.com.brew.brassia.fieldfeedback.domain.ActionOutcome;
import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.ComplaintCategory;
import br.com.brew.brassia.fieldfeedback.domain.ComplaintStatus;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import br.com.brew.brassia.fieldfeedback.domain.RequiredAction;
import br.com.brew.brassia.fieldfeedback.domain.SampleRetention;
import br.com.brew.brassia.fieldfeedback.domain.Severity;
import br.com.brew.brassia.fieldfeedback.domain.StorageReport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reclamações em PostgreSQL (FLD-001).
 *
 * <p><strong>O contato tem métodos próprios e nunca é carregado junto.</strong> Não é economia de
 * consulta: é o que garante que quem abre a reclamação para analisar off-flavor não traz dado pessoal na
 * memória sem ter pedido — e, portanto, sem ter sido auditado.
 *
 * <p>As ações exigidas não são gravadas: derivam de severidade e categoria. Grava-se o <em>destino</em>
 * de cada uma. Gravar a lista abriria a possibilidade de uma reclamação de corpo estranho com exigências
 * editadas para vazia.
 */
@Repository
class JdbcComplaintRepository implements ComplaintRepository {

    private final JdbcClient jdbc;

    JdbcComplaintRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(FieldComplaint complaint) {
        jdbc.sql("""
                INSERT INTO field_complaint (id, brewery_id, batch_id, reference, category, severity,
                        description, storage_temperature_celsius, storage_days_since_purchase,
                        storage_exposed_to_light, storage_notes, sample_status, sample_location,
                        status, registered_by, registered_at)
                VALUES (:id, :brewery, :batch, :reference, :category, :severity, :description,
                        :temperature, :days, :light, :notes, :sampleStatus, :sampleLocation,
                        :status, :by, :at)
                """)
                .param("id", complaint.id())
                .param("brewery", complaint.breweryId())
                .param("batch", complaint.batchId())
                .param("reference", complaint.reference().orElse(null))
                .param("category", complaint.category().name())
                .param("severity", complaint.severity().name())
                .param("description", complaint.description())
                .param("temperature", complaint.storage().approximateTemperatureCelsius())
                .param("days", complaint.storage().daysSincePurchase())
                .param("light", complaint.storage().exposedToLight())
                .param("notes", complaint.storage().notes())
                .param("sampleStatus", complaint.sample().status().name())
                .param("sampleLocation", complaint.sample().location())
                .param("status", complaint.status().name())
                .param("by", complaint.registeredBy())
                .param("at", Timestamp.from(complaint.registeredAt()))
                .update();
    }

    @Override
    public void updateProgress(FieldComplaint complaint) {
        jdbc.sql("""
                UPDATE field_complaint
                SET status = :status, closing_note = :note, closed_by = :closedBy,
                    closed_at = :closedAt
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", complaint.status().name())
                .param("note", complaint.closingNote().orElse(null))
                .param("closedBy", complaint.closedBy().orElse(null))
                .param("closedAt", complaint.closedAt().map(Timestamp::from).orElse(null))
                .param("id", complaint.id())
                .param("brewery", complaint.breweryId())
                .update();

        for (var outcome : complaint.outcomes()) {
            // ON CONFLICT DO UPDATE porque um destino pode ser revisto enquanto a reclamação está
            // aberta — trocar uma dispensa por uma quarentena de fato é correção legítima, e o registro
            // anterior fica na auditoria.
            jdbc.sql("""
                    INSERT INTO field_complaint_action (complaint_id, action, fulfilled, reference_id,
                            justification, decided_by, decided_at)
                    VALUES (:complaint, :action, :fulfilled, :reference, :justification, :by, :at)
                    ON CONFLICT (complaint_id, action) DO UPDATE
                    SET fulfilled = EXCLUDED.fulfilled, reference_id = EXCLUDED.reference_id,
                        justification = EXCLUDED.justification, decided_by = EXCLUDED.decided_by,
                        decided_at = EXCLUDED.decided_at
                    """)
                    .param("complaint", complaint.id())
                    .param("action", outcome.action().name())
                    .param("fulfilled", outcome.fulfilled())
                    .param("reference", outcome.referenceId())
                    .param("justification", outcome.justification())
                    .param("by", outcome.decidedBy())
                    .param("at", Timestamp.from(outcome.decidedAt()))
                    .update();
        }
    }

    @Override
    public Optional<FieldComplaint> find(UUID breweryId, UUID complaintId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", complaintId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public Optional<FieldComplaint> findForUpdate(UUID breweryId, UUID complaintId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery FOR UPDATE")
                .param("id", complaintId).param("brewery", breweryId)
                .query(this::map).optional();
    }

    @Override
    public List<FieldComplaint> list(UUID breweryId, UUID batchId) {
        var sql = batchId == null
                ? SELECT + " WHERE brewery_id = :brewery ORDER BY registered_at DESC"
                : SELECT + " WHERE brewery_id = :brewery AND batch_id = :batch "
                        + "ORDER BY registered_at DESC";
        var spec = jdbc.sql(sql).param("brewery", breweryId);
        if (batchId != null) {
            spec = spec.param("batch", batchId);
        }
        return spec.query(this::map).list();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, batch_id, reference, category, severity, description,
                   storage_temperature_celsius, storage_days_since_purchase, storage_exposed_to_light,
                   storage_notes, sample_status, sample_location, status, closing_note, closed_by,
                   closed_at, registered_by, registered_at
            FROM field_complaint
            """;

    private FieldComplaint map(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var storage = new StorageReport(
                rs.getBigDecimal("storage_temperature_celsius"),
                (Integer) rs.getObject("storage_days_since_purchase"),
                (Boolean) rs.getObject("storage_exposed_to_light"),
                rs.getString("storage_notes"));
        var sample = new SampleRetention(
                SampleRetention.Status.valueOf(rs.getString("sample_status")),
                rs.getString("sample_location"));
        return FieldComplaint.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getString("reference"),
                ComplaintCategory.valueOf(rs.getString("category")),
                Severity.valueOf(rs.getString("severity")),
                rs.getString("description"),
                storage,
                sample,
                ComplaintStatus.valueOf(rs.getString("status")),
                outcomesOf(id),
                rs.getString("closing_note"),
                rs.getObject("closed_by", UUID.class),
                instantOf(rs.getTimestamp("closed_at")),
                rs.getObject("registered_by", UUID.class),
                rs.getTimestamp("registered_at").toInstant());
    }

    private List<ActionOutcome> outcomesOf(UUID complaintId) {
        return jdbc.sql("""
                SELECT action, fulfilled, reference_id, justification, decided_by, decided_at
                FROM field_complaint_action WHERE complaint_id = :complaint ORDER BY action
                """)
                .param("complaint", complaintId)
                .query((rs, n) -> new ActionOutcome(
                        RequiredAction.valueOf(rs.getString("action")),
                        rs.getBoolean("fulfilled"),
                        rs.getObject("reference_id", UUID.class),
                        rs.getString("justification"),
                        rs.getObject("decided_by", UUID.class),
                        rs.getTimestamp("decided_at").toInstant()))
                .list();
    }

    // --- dado pessoal ---

    @Override
    public void insertContact(ComplainantContact contact) {
        jdbc.sql("""
                INSERT INTO field_complaint_contact (complaint_id, name, email, phone, address,
                        erased, recorded_by, recorded_at)
                VALUES (:complaint, :name, :email, :phone, :address, FALSE, :by, :at)
                """)
                .param("complaint", contact.complaintId())
                .param("name", contact.name().orElse(null))
                .param("email", contact.email().orElse(null))
                .param("phone", contact.phone().orElse(null))
                .param("address", contact.address().orElse(null))
                .param("by", contact.recordedBy())
                .param("at", Timestamp.from(contact.recordedAt()))
                .update();
    }

    /** A junção com `field_complaint` existe só para o filtro por cervejaria — o contato não a atravessa. */
    @Override
    public Optional<ComplainantContact> findContact(UUID breweryId, UUID complaintId) {
        return jdbc.sql("""
                SELECT c.complaint_id, c.name, c.email, c.phone, c.address, c.erased, c.erased_at,
                       c.recorded_by, c.recorded_at
                FROM field_complaint_contact c
                JOIN field_complaint f ON f.id = c.complaint_id
                WHERE c.complaint_id = :complaint AND f.brewery_id = :brewery
                """)
                .param("complaint", complaintId).param("brewery", breweryId)
                .query((rs, n) -> ComplainantContact.reconstitute(
                        rs.getObject("complaint_id", UUID.class),
                        rs.getString("name"), rs.getString("email"), rs.getString("phone"),
                        rs.getString("address"), rs.getBoolean("erased"),
                        instantOf(rs.getTimestamp("erased_at")),
                        rs.getObject("recorded_by", UUID.class),
                        rs.getTimestamp("recorded_at").toInstant()))
                .optional();
    }

    /**
     * Apaga o conteúdo e mantém a linha.
     *
     * <p>{@code UPDATE ... SET NULL} e não {@code DELETE}: o fato de ter havido contato, e de ele ter
     * sido apagado, é o que torna o apagamento demonstrável — inclusive para quem o pediu.
     */
    @Override
    public void eraseContact(UUID breweryId, UUID complaintId, Instant at) {
        jdbc.sql("""
                UPDATE field_complaint_contact c
                SET name = NULL, email = NULL, phone = NULL, address = NULL,
                    erased = TRUE, erased_at = :at
                FROM field_complaint f
                WHERE c.complaint_id = :complaint AND f.id = c.complaint_id
                  AND f.brewery_id = :brewery
                """)
                .param("at", Timestamp.from(at))
                .param("complaint", complaintId).param("brewery", breweryId)
                .update();
    }

    private static Instant instantOf(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
