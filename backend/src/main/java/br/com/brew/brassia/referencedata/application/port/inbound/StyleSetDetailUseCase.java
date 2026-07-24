package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.StyleRange;
import java.util.List;
import java.util.UUID;

/** Detalhe de um conjunto com seus estilos (respeitando o gate de conteúdo já aplicado). */
public interface StyleSetDetailUseCase {

    Detail handle(Query query);

    record Query(UUID breweryId, UUID styleSetId) {}

    record StyleView(String code, String name, String family, String category, StyleRange og, StyleRange fg,
            StyleRange abv, StyleRange ibu, StyleRange color, String generalImpression, boolean hasDetailedProfile) {}

    record Detail(UUID id, boolean global, String authority, String edition, String language, String permissionStatus,
            String status, List<StyleView> styles) {}
}
