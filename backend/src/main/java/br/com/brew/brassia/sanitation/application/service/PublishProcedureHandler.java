package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.PublishProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import java.util.Map;
import java.util.Objects;

/** Publica um POP (CLN-001): DRAFT → PUBLISHED, guardado pelo estado (congela a versão). */
public final class PublishProcedureHandler implements PublishProcedureUseCase {

    private final ProcedureRepository repository;
    private final AuditTrail audit;

    public PublishProcedureHandler(ProcedureRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var procedure = repository.findById(command.breweryId(), command.procedureId())
                .orElseThrow(() -> new IllegalArgumentException("POP inexistente"));
        if (procedure.steps().isEmpty()) {
            throw new IllegalStateException("POP sem etapas não pode ser publicado");
        }

        if (!repository.markPublished(command.breweryId(), command.procedureId())) {
            throw new IllegalStateException("POP não está em rascunho");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.procedure.publish",
                "sanitation.procedure", procedure.id().value().toString(),
                Map.of("code", procedure.code(), "version", String.valueOf(procedure.version()))));
    }
}
