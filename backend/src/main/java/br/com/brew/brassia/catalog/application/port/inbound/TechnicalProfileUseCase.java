package br.com.brew.brassia.catalog.application.port.inbound;

import br.com.brew.brassia.catalog.domain.PropertyRange;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TechnicalProfileUseCase {

    Optional<ProfileView> handle(Query query);

    record Query(UUID breweryId, UUID ingredientId) {}

    record ProfileView(UUID ingredientId, String manufacturer, String origin, String form, String purpose,
            String laboratory, String labCode, Map<String, PropertyRange> ranges, List<String> descriptors,
            UUID sourceId, String sourceName, String status) {}
}
