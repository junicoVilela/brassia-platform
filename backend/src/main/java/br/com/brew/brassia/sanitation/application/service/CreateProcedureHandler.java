package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.Map;
import java.util.Objects;

/**
 * Cria um POP em rascunho (CLN-001). Versionamento por código: código novo →
 * versão 1; código com versão publicada → próxima versão; código com rascunho
 * aberto → conflito (só um rascunho por vez).
 */
public final class CreateProcedureHandler implements CreateProcedureUseCase {

    private final ProcedureRepository repository;
    private final AuditTrail audit;

    public CreateProcedureHandler(ProcedureRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var latest = repository.findLatestByCode(command.breweryId(), command.code());
        int version = 1;
        if (latest.isPresent()) {
            if (latest.get().draftStatus()) {
                throw new IllegalStateException("já existe um rascunho aberto para este código");
            }
            version = latest.get().version() + 1;
        }

        var procedure = CleaningProcedure.draft(command.breweryId(), command.code(), command.name(), version,
                ProcedureSteps.from(command.steps()));
        repository.insert(procedure);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.procedure.create",
                "sanitation.procedure", procedure.id().value().toString(),
                Map.of("code", procedure.code(), "version", String.valueOf(version))));

        return new Result(procedure.id().value(), version);
    }
}
