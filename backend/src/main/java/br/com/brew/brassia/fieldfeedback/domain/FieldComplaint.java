package br.com.brew.brassia.fieldfeedback.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma reclamação de campo ligada a um lote (FLD-001).
 *
 * <p><strong>Não há dado pessoal aqui.</strong> Nome, telefone e endereço de quem reclamou vivem noutro
 * agregado, com permissão própria e acesso auditado — ver {@link ComplainantContact}. A separação não é
 * organização de código: é o que permite apagar a pessoa sem apagar o registro de qualidade. Uma
 * investigação de corpo estranho precisa sobreviver por anos; o telefone de quem ligou, não.
 *
 * <p><strong>A severidade não escolhe o que fazer — ela determina.</strong> As ações exigidas são
 * derivadas em {@link RequiredAction#of}, e a reclamação não encerra enquanto cada uma não for atendida
 * ou dispensada com justificativa assinada.
 */
public final class FieldComplaint {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final String reference;
    private final ComplaintCategory category;
    private final Severity severity;
    private final String description;
    private final StorageReport storage;
    private final SampleRetention sample;
    private final List<RequiredAction> requiredActions;
    private final UUID registeredBy;
    private final Instant registeredAt;

    private final Map<RequiredAction, ActionOutcome> outcomes = new LinkedHashMap<>();

    private ComplaintStatus status;
    private String closingNote;
    private UUID closedBy;
    private Instant closedAt;

    private FieldComplaint(UUID id, UUID breweryId, UUID batchId, String reference,
            ComplaintCategory category, Severity severity, String description, StorageReport storage,
            SampleRetention sample, ComplaintStatus status, UUID registeredBy, Instant registeredAt) {
        this.id = id;
        this.breweryId = breweryId;
        this.batchId = batchId;
        this.reference = reference;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.storage = storage;
        this.sample = sample;
        this.requiredActions = RequiredAction.of(severity, category);
        this.status = status;
        this.registeredBy = registeredBy;
        this.registeredAt = registeredAt;
    }

    public static FieldComplaint register(UUID id, UUID breweryId, UUID batchId, String reference,
            ComplaintCategory category, Severity severity, String description, StorageReport storage,
            SampleRetention sample, UUID registeredBy, Instant registeredAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(registeredBy, "registeredBy");
        Objects.requireNonNull(registeredAt, "registeredAt");

        var text = Objects.requireNonNull(description, "description").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("a descrição da reclamação não pode ser vazia");
        }
        return new FieldComplaint(id, breweryId, batchId, reference, category, severity, text,
                storage == null ? StorageReport.unknown() : storage,
                sample == null ? SampleRetention.unknown() : sample,
                ComplaintStatus.OPEN, registeredBy, registeredAt);
    }

    /** Reconstrói do banco sem revalidar. As ações exigidas são derivadas de novo, não lidas. */
    public static FieldComplaint reconstitute(UUID id, UUID breweryId, UUID batchId, String reference,
            ComplaintCategory category, Severity severity, String description, StorageReport storage,
            SampleRetention sample, ComplaintStatus status, List<ActionOutcome> outcomes,
            String closingNote, UUID closedBy, Instant closedAt, UUID registeredBy,
            Instant registeredAt) {
        var complaint = new FieldComplaint(id, breweryId, batchId, reference, category, severity,
                description, storage, sample, status, registeredBy, registeredAt);
        outcomes.forEach(outcome -> complaint.outcomes.put(outcome.action(), outcome));
        complaint.closingNote = closingNote;
        complaint.closedBy = closedBy;
        complaint.closedAt = closedAt;
        return complaint;
    }

    public void startAnalysis() {
        if (status != ComplaintStatus.OPEN) {
            throw new IllegalComplaintTransitionException(status, ComplaintStatus.UNDER_ANALYSIS);
        }
        status = ComplaintStatus.UNDER_ANALYSIS;
    }

    /** Registra que uma ação exigida foi atendida, apontando para o que foi criado. */
    public void fulfill(RequiredAction action, UUID referenceId, UUID actor, Instant at) {
        requireApplicable(action);
        outcomes.put(action, ActionOutcome.fulfilled(action, referenceId, actor, at));
    }

    /** Dispensa uma ação exigida, com justificativa assinada. */
    public void waive(RequiredAction action, String justification, UUID actor, Instant at) {
        requireApplicable(action);
        outcomes.put(action, ActionOutcome.waived(action, justification, actor, at));
    }

    /**
     * Encerra.
     *
     * @throws PendingActionsException se alguma ação exigida segue sem destino. É o que impede uma
     *                                 reclamação de corpo estranho de ser encerrada como "cliente
     *                                 contatado".
     */
    public void close(String note, UUID actor, Instant at) {
        if (status == ComplaintStatus.CLOSED) {
            throw new IllegalComplaintTransitionException(status, ComplaintStatus.CLOSED);
        }
        var pending = pendingActions();
        if (!pending.isEmpty()) {
            throw new PendingActionsException(pending);
        }
        status = ComplaintStatus.CLOSED;
        closingNote = note == null ? null : note.trim();
        closedBy = Objects.requireNonNull(actor, "actor");
        closedAt = Objects.requireNonNull(at, "at");
    }

    /** As exigidas que ainda não têm destino. */
    public List<RequiredAction> pendingActions() {
        var pending = new ArrayList<RequiredAction>();
        for (var action : requiredActions) {
            if (!outcomes.containsKey(action)) {
                pending.add(action);
            }
        }
        return pending;
    }

    private void requireApplicable(RequiredAction action) {
        if (status == ComplaintStatus.CLOSED) {
            throw new IllegalComplaintTransitionException(status, ComplaintStatus.CLOSED);
        }
        // Registrar destino para ação que este caso não exige inventaria histórico: pareceria que a
        // quarentena foi cogitada e dispensada, quando ela nunca esteve em questão.
        if (!requiredActions.contains(action)) {
            throw new IllegalArgumentException("esta reclamação não exige " + action);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID batchId() {
        return batchId;
    }

    public Optional<String> reference() {
        return Optional.ofNullable(reference);
    }

    public ComplaintCategory category() {
        return category;
    }

    public Severity severity() {
        return severity;
    }

    public String description() {
        return description;
    }

    public StorageReport storage() {
        return storage;
    }

    public SampleRetention sample() {
        return sample;
    }

    public List<RequiredAction> requiredActions() {
        return requiredActions;
    }

    public List<ActionOutcome> outcomes() {
        return List.copyOf(outcomes.values());
    }

    public ComplaintStatus status() {
        return status;
    }

    public Optional<String> closingNote() {
        return Optional.ofNullable(closingNote);
    }

    public Optional<UUID> closedBy() {
        return Optional.ofNullable(closedBy);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public UUID registeredBy() {
        return registeredBy;
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    /** Transição que o estado atual não permite. */
    public static final class IllegalComplaintTransitionException extends RuntimeException {

        private final ComplaintStatus current;
        private final ComplaintStatus attempted;

        IllegalComplaintTransitionException(ComplaintStatus current, ComplaintStatus attempted) {
            super("reclamação em " + current + " não pode ir para " + attempted);
            this.current = current;
            this.attempted = attempted;
        }

        public ComplaintStatus current() {
            return current;
        }

        public ComplaintStatus attempted() {
            return attempted;
        }
    }
}
