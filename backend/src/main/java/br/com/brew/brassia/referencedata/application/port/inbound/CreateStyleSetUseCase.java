package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.StyleAuthority;
import br.com.brew.brassia.referencedata.domain.StyleRange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cria um conjunto de estilos (rascunho). A permissão é herdada da fonte (gate de conteúdo). */
public interface CreateStyleSetUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID sourceId, StyleAuthority authority, String edition,
            String language, Instant effectiveFrom, Instant effectiveTo, String attribution, List<StyleSpec> styles) {}

    record StyleSpec(String code, String name, String family, String category, StyleRange og, StyleRange fg,
            StyleRange abv, StyleRange ibu, StyleRange color, String generalImpression, String detailedProfile) {}

    record Result(UUID id) {}
}
