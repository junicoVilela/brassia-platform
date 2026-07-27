package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.UpdateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import java.util.Map;
import java.util.Objects;

/** Atualiza um POP em rascunho (CLN-001); publicado é imutável (domínio → 409). */
public final class UpdateProcedureHandler implements UpdateProcedureUseCase {

    private final ProcedureRepository repository;
    private final AuditTrail audit;

    public UpdateProcedureHandler(ProcedureRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var procedure = repository.findById(command.breweryId(), command.procedureId())
                .orElseThrow(() -> new IllegalArgumentException("POP inexistente"));

        procedure.update(command.name(), ProcedureSteps.from(command.steps()));
        repository.update(procedure);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.procedure.update",
                "sanitation.procedure", procedure.id().value().toString(),
                Map.of("code", procedure.code(), "version", String.valueOf(procedure.version()))));
    }
}
