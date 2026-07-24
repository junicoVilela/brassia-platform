package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.StyleSetDetailUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import br.com.brew.brassia.referencedata.domain.Style;
import java.util.Objects;

public final class StyleSetDetailHandler implements StyleSetDetailUseCase {

    private final StyleSetRepository styleSets;

    public StyleSetDetailHandler(StyleSetRepository styleSets) {
        this.styleSets = Objects.requireNonNull(styleSets);
    }

    @Override
    public Detail handle(Query query) {
        var set = styleSets.findVisible(query.breweryId(), query.styleSetId())
                .orElseThrow(() -> new IllegalArgumentException("conjunto inexistente ou fora do escopo"));
        var styles = set.styles().stream().map(StyleSetDetailHandler::toView).toList();
        return new Detail(set.id().value(), set.isGlobal(), set.authority().name(), set.edition(), set.language(),
                set.permissionStatus().name(), set.status().name(), styles);
    }

    private static StyleView toView(Style s) {
        return new StyleView(s.code(), s.name(), s.family(), s.category(), s.og(), s.fg(), s.abv(), s.ibu(), s.color(),
                s.generalImpression(), s.detailedProfile() != null);
    }
}
