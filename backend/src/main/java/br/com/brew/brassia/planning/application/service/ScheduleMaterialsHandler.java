package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.application.port.inbound.MaterialRequirementUseCase;
import br.com.brew.brassia.planning.application.port.inbound.ScheduleMaterialsUseCase;
import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import java.util.List;
import java.util.Objects;

/**
 * Necessidade de materiais de uma entrada da agenda: reaproveita a receita e o
 * volume planejados (PLN-001) e delega o cálculo ao {@link MaterialRequirementUseCase}.
 */
public final class ScheduleMaterialsHandler implements ScheduleMaterialsUseCase {

    private final ScheduleEntryRepository repository;
    private final MaterialRequirementUseCase requirement;

    public ScheduleMaterialsHandler(ScheduleEntryRepository repository, MaterialRequirementUseCase requirement) {
        this.repository = Objects.requireNonNull(repository);
        this.requirement = Objects.requireNonNull(requirement);
    }

    @Override
    public List<MaterialRequirementUseCase.Line> handle(Query query) {
        var entry = repository.findById(query.breweryId(), query.scheduleEntryId())
                .orElseThrow(() -> new IllegalArgumentException("entrada da agenda inexistente"));

        return requirement.handle(new MaterialRequirementUseCase.Query(
                query.breweryId(), entry.recipeId(), entry.plannedVolumeLiters(), query.lossPercent()));
    }
}
