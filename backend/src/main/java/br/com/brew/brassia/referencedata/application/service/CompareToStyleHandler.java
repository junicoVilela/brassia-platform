package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.CompareToStyleUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import br.com.brew.brassia.referencedata.domain.Style;
import java.util.Objects;

public final class CompareToStyleHandler implements CompareToStyleUseCase {

    private final StyleSetRepository styleSets;

    public CompareToStyleHandler(StyleSetRepository styleSets) {
        this.styleSets = Objects.requireNonNull(styleSets);
    }

    @Override
    public Result handle(Query query) {
        var set = styleSets.findVisible(query.breweryId(), query.styleSetId())
                .orElseThrow(() -> new IllegalArgumentException("conjunto inexistente ou fora do escopo"));
        Style style = set.styles().stream()
                .filter(s -> s.code().equalsIgnoreCase(query.styleCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("estilo inexistente no conjunto"));
        var checks = style.evaluate(query.og(), query.fg(), query.abv(), query.ibu(), query.colorEbc());
        return new Result(style.code(), style.name(), checks);
    }
}
