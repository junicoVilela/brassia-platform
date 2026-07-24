package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.CreateStyleSetUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import br.com.brew.brassia.referencedata.domain.PermissionStatus;
import br.com.brew.brassia.referencedata.domain.Style;
import br.com.brew.brassia.referencedata.domain.StyleSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CreateStyleSetHandler implements CreateStyleSetUseCase {

    private final ReferenceSourceRepository sources;
    private final StyleSetRepository styleSets;
    private final AuditTrail audit;

    public CreateStyleSetHandler(ReferenceSourceRepository sources, StyleSetRepository styleSets, AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.styleSets = Objects.requireNonNull(styleSets);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = sources.findVisible(command.breweryId(), command.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));
        if (styleSets.existsByCoordinates(command.breweryId(), command.authority(), command.edition(),
                command.language())) {
            throw new IllegalStateException("já existe um conjunto para essa autoridade/edição/idioma no escopo");
        }

        PermissionStatus permission = source.permissionStatus();
        List<Style> styles = command.styles().stream()
                .map(spec -> Style.create(spec.code(), spec.name(), spec.family(), spec.category(), spec.og(),
                        spec.fg(), spec.abv(), spec.ibu(), spec.color(), spec.generalImpression(),
                        spec.detailedProfile(), permission))
                .toList();
        var set = StyleSet.draft(command.breweryId(), source.id(), command.authority(), command.edition(),
                command.language(), command.effectiveFrom(), command.effectiveTo(), command.attribution(), permission,
                styles);
        styleSets.insert(set);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.style_set.create",
                "style_set", set.id().value().toString(),
                Map.of("authority", set.authority().name(), "edition", set.edition(),
                        "styles", Integer.toString(styles.size()))));

        return new Result(set.id().value());
    }
}
