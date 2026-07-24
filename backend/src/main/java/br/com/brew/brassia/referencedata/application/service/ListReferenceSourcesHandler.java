package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceSourcesUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.ReferenceSource;
import java.util.Objects;

public final class ListReferenceSourcesHandler implements ListReferenceSourcesUseCase {

    private final ReferenceSourceRepository repository;

    public ListReferenceSourcesHandler(ReferenceSourceRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var content = repository.findPage(query.breweryId(), query.page(), query.size()).stream()
                .map(ListReferenceSourcesHandler::toView)
                .toList();
        return new Result(content, repository.count(query.breweryId()));
    }

    private static SourceView toView(ReferenceSource s) {
        return new SourceView(s.id().value(), s.isGlobal(), s.type().name(), s.name(), s.owner(), s.url(),
                s.license().licenseName(), s.permissionStatus().name(), s.license().attribution());
    }
}
