package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.ListStyleSetsUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import br.com.brew.brassia.referencedata.domain.StyleSet;
import java.util.Objects;

public final class ListStyleSetsHandler implements ListStyleSetsUseCase {

    private final StyleSetRepository styleSets;

    public ListStyleSetsHandler(StyleSetRepository styleSets) {
        this.styleSets = Objects.requireNonNull(styleSets);
    }

    @Override
    public Result handle(Query query) {
        var content = styleSets.findPage(query.breweryId(), query.page(), query.size()).stream()
                .map(ListStyleSetsHandler::toView)
                .toList();
        return new Result(content, styleSets.count(query.breweryId()));
    }

    private static SetView toView(StyleSet s) {
        return new SetView(s.id().value(), s.isGlobal(), s.authority().name(), s.edition(), s.language(),
                s.permissionStatus().name(), s.status().name(), s.publishedAt());
    }
}
