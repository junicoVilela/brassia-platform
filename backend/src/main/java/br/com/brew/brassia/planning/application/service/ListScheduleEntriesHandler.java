package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.application.port.inbound.ListScheduleEntriesUseCase;
import br.com.brew.brassia.planning.application.port.outbound.ScheduleEntryRepository;
import br.com.brew.brassia.planning.domain.ScheduleEntry;
import java.util.List;
import java.util.Objects;

public final class ListScheduleEntriesHandler implements ListScheduleEntriesUseCase {

    private final ScheduleEntryRepository repository;

    public ListScheduleEntriesHandler(ScheduleEntryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<Item> handle(Query query) {
        return repository.findBetween(query.breweryId(), query.from(), query.to()).stream()
                .map(ListScheduleEntriesHandler::toItem)
                .toList();
    }

    private static Item toItem(ScheduleEntry e) {
        return new Item(e.id().value(), e.recipeId(), e.equipmentId(), e.assignedUserId(),
                e.plannedVolumeLiters(), e.window().start(), e.window().end(), e.status().name());
    }
}
