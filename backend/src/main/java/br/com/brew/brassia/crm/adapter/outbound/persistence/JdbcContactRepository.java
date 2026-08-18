package br.com.brew.brassia.crm.adapter.outbound.persistence;

import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.domain.ConsentDecision;
import br.com.brew.brassia.crm.domain.ConsentEntry;
import br.com.brew.brassia.crm.domain.ConsentLedger;
import br.com.brew.brassia.crm.domain.Contact;
import br.com.brew.brassia.crm.domain.ContactPurpose;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContactRepository implements ContactRepository {

    private final JdbcClient jdbc;

    JdbcContactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Contact contact, UUID actorId) {
        jdbc.sql("""
                INSERT INTO crm_contact (id, brewery_id, customer_id, name, email, phone, role,
                                         created_by, created_at)
                VALUES (:id, :brewery, :customer, :name, :email, :phone, :role, :by, :at)
                """)
                .param("id", contact.id())
                .param("brewery", contact.breweryId())
                .param("customer", contact.customerId())
                .param("name", contact.name().orElse(null))
                .param("email", contact.email().orElse(null))
                .param("phone", contact.phone().orElse(null))
                .param("role", contact.role().orElse(null))
                .param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public Optional<Contact> find(UUID breweryId, UUID id) {
        var contact = jdbc.sql("""
                SELECT id, brewery_id, customer_id, name, email, phone, role, anonymized_at
                FROM crm_contact WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", id).param("brewery", breweryId)
                .query((rs, row) -> map(rs, loadConsents(breweryId, id))).optional();
        return contact;
    }

    @Override
    public List<Contact> listByCustomer(UUID breweryId, UUID customerId) {
        return jdbc.sql("""
                SELECT id, brewery_id, customer_id, name, email, phone, role, anonymized_at
                FROM crm_contact
                WHERE brewery_id = :brewery AND customer_id = :customer
                ORDER BY anonymized_at NULLS FIRST, name
                """)
                .param("brewery", breweryId).param("customer", customerId)
                .query((rs, row) -> map(rs, loadConsents(breweryId, rs.getObject("id", UUID.class))))
                .list();
    }

    @Override
    public void appendConsent(UUID breweryId, UUID contactId, ConsentEntry entry) {
        // Só INSERT. Não existe caminho de UPDATE nem DELETE para esta tabela, e é isso que faz o
        // histórico responder "ela aceitava quando mandamos?" em vez de só "ela aceita hoje?".
        jdbc.sql("""
                INSERT INTO crm_consent_entry (id, brewery_id, contact_id, purpose, decision, decided_at,
                                               source, recorded_by, recorded_at)
                VALUES (:id, :brewery, :contact, :purpose, :decision, :decided, :source, :by, :at)
                """)
                .param("id", UUID.randomUUID())
                .param("brewery", breweryId)
                .param("contact", contactId)
                .param("purpose", entry.purpose().name())
                .param("decision", entry.decision().name())
                .param("decided", Timestamp.from(entry.at()))
                .param("source", entry.source())
                .param("by", entry.recordedBy())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void anonymize(Contact contact) {
        // Os campos vão a NULL de verdade, e não para uma máscara. Guardar "ANÔNIMO" no lugar do nome
        // manteria a linha ocupando o mesmo espaço com um dado falso; o CHECK da migration exige que
        // todos os campos pessoais fiquem nulos quando anonymized_at existe.
        jdbc.sql("""
                UPDATE crm_contact
                SET name = NULL, email = NULL, phone = NULL, role = NULL, anonymized_at = :at
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("at", Timestamp.from(contact.anonymizedAt().orElseThrow()))
                .param("id", contact.id())
                .param("brewery", contact.breweryId())
                .update();
    }

    private ConsentLedger loadConsents(UUID breweryId, UUID contactId) {
        var entries = jdbc.sql("""
                SELECT purpose, decision, decided_at, source, recorded_by
                FROM crm_consent_entry
                WHERE brewery_id = :brewery AND contact_id = :contact
                ORDER BY decided_at
                """)
                .param("brewery", breweryId).param("contact", contactId)
                .query((rs, row) -> new ConsentEntry(ContactPurpose.valueOf(rs.getString("purpose")),
                        ConsentDecision.valueOf(rs.getString("decision")),
                        rs.getTimestamp("decided_at").toInstant(), rs.getString("source"),
                        rs.getObject("recorded_by", UUID.class)))
                .list();
        return ConsentLedger.reconstitute(entries);
    }

    private static Contact map(ResultSet rs, ConsentLedger consents) throws SQLException {
        var anonymizedAt = rs.getTimestamp("anonymized_at");
        return Contact.reconstitute(rs.getObject("id", UUID.class), rs.getObject("brewery_id", UUID.class),
                rs.getObject("customer_id", UUID.class), rs.getString("name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("role"),
                anonymizedAt == null ? null : anonymizedAt.toInstant(), consents);
    }

    @Override
    public List<ContactRelationship> liveContacts(UUID breweryId) {
        // O consentimento mais recente por contato vem numa junção lateral: um SELECT por contato seria
        // o N+1 que a REL-002 já custou uma vez.
        return jdbc.sql("""
                SELECT c.id, c.customer_id, c.name, ultimo.decided_at::date AS last_consent_on
                FROM crm_contact c
                LEFT JOIN LATERAL (
                    SELECT e.decided_at FROM crm_consent_entry e
                    WHERE e.contact_id = c.id ORDER BY e.decided_at DESC LIMIT 1
                ) ultimo ON TRUE
                WHERE c.brewery_id = :brewery AND c.anonymized_at IS NULL
                ORDER BY c.created_at
                """)
                .param("brewery", breweryId)
                .query((rs, row) -> new ContactRelationship(rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getString("name"),
                        rs.getObject("last_consent_on", java.time.LocalDate.class)))
                .list();
    }
}
