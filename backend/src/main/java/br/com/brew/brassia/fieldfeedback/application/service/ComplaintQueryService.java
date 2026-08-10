package br.com.brew.brassia.fieldfeedback.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintQueries;
import br.com.brew.brassia.fieldfeedback.application.port.outbound.ComplaintRepository;
import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de reclamação (FLD-001).
 *
 * <p><strong>Ler dado pessoal é auditado; ler a reclamação não.</strong> A assimetria é o desenho: a
 * reclamação é consultada o tempo todo por quem investiga, e auditar tudo produziria um volume em que a
 * leitura do contato — que é o ato que importa — ficaria invisível no meio.
 */
public final class ComplaintQueryService implements ComplaintQueries {

    private final ComplaintRepository complaints;
    private final AuditTrail audit;

    public ComplaintQueryService(ComplaintRepository complaints, AuditTrail audit) {
        this.complaints = Objects.requireNonNull(complaints, "complaints");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public Optional<FieldComplaint> find(UUID breweryId, UUID complaintId) {
        return complaints.find(breweryId, complaintId);
    }

    @Override
    public List<FieldComplaint> list(UUID breweryId, UUID batchId) {
        return complaints.list(breweryId, batchId);
    }

    @Override
    public Optional<ComplainantContact> contact(UUID breweryId, UUID complaintId, UUID actor) {
        var contact = complaints.findContact(breweryId, complaintId);
        // Auditado ANTES de devolver, e mesmo quando não há contato: a tentativa de acesso é o fato
        // relevante. Registrar só o acerto deixaria de fora quem varre reclamações procurando dados.
        audit.record(AuditEvent.success(breweryId, actor, "feedback.contact.read",
                "field_complaint_contact", complaintId.toString(),
                Map.of("found", String.valueOf(contact.isPresent()),
                        "erased", String.valueOf(contact.map(ComplainantContact::erased).orElse(false)))));
        return contact;
    }
}
