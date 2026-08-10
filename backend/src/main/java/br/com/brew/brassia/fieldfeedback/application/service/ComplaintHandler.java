package br.com.brew.brassia.fieldfeedback.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fieldfeedback.application.port.inbound.ComplaintCommands;
import br.com.brew.brassia.fieldfeedback.application.port.outbound.ComplaintRepository;
import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import br.com.brew.brassia.fieldfeedback.domain.RequiredAction;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintBatchException;
import br.com.brew.brassia.fieldfeedback.domain.UnknownComplaintException;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reclamações de campo (FLD-001).
 *
 * <p><strong>O registro do contato acontece na mesma transação e é gravado à parte.</strong> As duas
 * coisas juntas: se a reclamação for gravada e o contato falhar, ficaria uma reclamação que parece
 * anônima — e ninguém saberia que havia alguém para retornar.
 */
public final class ComplaintHandler implements ComplaintCommands {

    private final ComplaintRepository complaints;
    private final BatchLookup batches;
    private final AuditTrail audit;
    private final Clock clock;

    public ComplaintHandler(ComplaintRepository complaints, BatchLookup batches, AuditTrail audit,
            Clock clock) {
        this.complaints = Objects.requireNonNull(complaints, "complaints");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public FieldComplaint register(RegisterCommand command) {
        Objects.requireNonNull(command, "command");
        if (!batches.exists(command.breweryId(), command.batchId())) {
            throw new UnknownComplaintBatchException(
                    "o lote da reclamação não existe nesta cervejaria: " + command.batchId());
        }
        var now = clock.instant();
        var complaint = FieldComplaint.register(UUID.randomUUID(), command.breweryId(),
                command.batchId(), command.reference(), command.category(), command.severity(),
                command.description(), command.storage(), command.sample(), command.actor(), now);
        complaints.insert(complaint);

        if (command.contact() != null) {
            var c = command.contact();
            complaints.insertContact(ComplainantContact.record(complaint.id(), c.name(), c.email(),
                    c.phone(), c.address(), command.actor(), now));
        }

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("batchId", complaint.batchId().toString());
        metadata.put("severity", complaint.severity().name());
        metadata.put("category", complaint.category().name());
        // As exigências vão para a auditoria no registro, e não só no encerramento: é o que mostra que
        // elas nasceram com o caso em vez de terem sido decididas depois, quando já se sabia o desfecho.
        metadata.put("requiredActions", complaint.requiredActions().stream().map(Enum::name)
                .collect(Collectors.joining(",")));
        // Deliberadamente ausente da auditoria: qualquer dado do contato. Uma trilha que registra o nome
        // de quem reclamou vira o vazamento que a separação das tabelas evitou.
        metadata.put("hasContact", String.valueOf(command.contact() != null));
        record(command.breweryId(), command.actor(), "feedback.complaint.register", complaint.id(),
                metadata);
        return complaint;
    }

    @Override
    public FieldComplaint startAnalysis(UUID breweryId, UUID complaintId, UUID actor) {
        var complaint = lockedOrFail(breweryId, complaintId);
        complaint.startAnalysis();
        complaints.updateProgress(complaint);
        record(breweryId, actor, "feedback.complaint.analyze", complaintId, Map.of());
        return complaint;
    }

    @Override
    public FieldComplaint fulfill(UUID breweryId, UUID complaintId, RequiredAction action,
            UUID referenceId, UUID actor) {
        var complaint = lockedOrFail(breweryId, complaintId);
        complaint.fulfill(action, referenceId, actor, clock.instant());
        complaints.updateProgress(complaint);
        record(breweryId, actor, "feedback.complaint.action.fulfill", complaintId,
                Map.of("action", action.name(), "referenceId", referenceId.toString()));
        return complaint;
    }

    /**
     * Dispensa uma exigência.
     *
     * <p>A justificativa vai inteira para a auditoria. Dispensar quarentena numa reclamação de corpo
     * estranho é a decisão mais consequente deste módulo, e ela precisa ser legível meses depois sem
     * depender de a reclamação ainda existir.
     */
    @Override
    public FieldComplaint waive(UUID breweryId, UUID complaintId, RequiredAction action,
            String justification, UUID actor) {
        var complaint = lockedOrFail(breweryId, complaintId);
        complaint.waive(action, justification, actor, clock.instant());
        complaints.updateProgress(complaint);
        record(breweryId, actor, "feedback.complaint.action.waive", complaintId,
                Map.of("action", action.name(), "justification", justification));
        return complaint;
    }

    @Override
    public FieldComplaint close(UUID breweryId, UUID complaintId, String note, UUID actor) {
        var complaint = lockedOrFail(breweryId, complaintId);
        complaint.close(note, actor, clock.instant());
        complaints.updateProgress(complaint);
        record(breweryId, actor, "feedback.complaint.close", complaintId, Map.of());
        return complaint;
    }

    private FieldComplaint lockedOrFail(UUID breweryId, UUID complaintId) {
        // Sem o FOR UPDATE, duas dispensas simultâneas da mesma exigência gravariam a segunda por cima
        // da primeira — e a justificativa registrada teria um autor que não a escreveu.
        return complaints.findForUpdate(breweryId, complaintId)
                .orElseThrow(() -> new UnknownComplaintException(complaintId));
    }

    private void record(UUID breweryId, UUID actor, String action, UUID complaintId,
            Map<String, String> metadata) {
        audit.record(AuditEvent.success(breweryId, actor, action, "field_complaint",
                complaintId.toString(), metadata));
    }
}
