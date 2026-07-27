package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.GetProcedureUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.Objects;
import java.util.UUID;

public final class GetProcedureHandler implements GetProcedureUseCase {

    private final ProcedureRepository repository;

    public GetProcedureHandler(ProcedureRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public CleaningProcedure handle(UUID breweryId, UUID procedureId) {
        return repository.findById(breweryId, procedureId)
                .orElseThrow(() -> new IllegalArgumentException("POP inexistente"));
    }
}
