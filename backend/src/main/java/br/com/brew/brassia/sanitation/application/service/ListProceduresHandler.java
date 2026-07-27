package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.ListProceduresUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListProceduresHandler implements ListProceduresUseCase {

    private final ProcedureRepository repository;

    public ListProceduresHandler(ProcedureRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<CleaningProcedure> handle(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
