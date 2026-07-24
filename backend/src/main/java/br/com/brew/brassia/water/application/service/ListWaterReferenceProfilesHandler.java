package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.ListWaterReferenceProfilesUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterReferenceProfileRepository;
import br.com.brew.brassia.water.domain.WaterReferenceProfile;
import java.util.Objects;

public final class ListWaterReferenceProfilesHandler implements ListWaterReferenceProfilesUseCase {

    private final WaterReferenceProfileRepository profiles;

    public ListWaterReferenceProfilesHandler(WaterReferenceProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles);
    }

    @Override
    public Result handle(Query query) {
        var content = profiles.findPage(query.breweryId(), query.page(), query.size()).stream()
                .map(ListWaterReferenceProfilesHandler::toView)
                .toList();
        return new Result(content, profiles.count(query.breweryId()));
    }

    private static ProfileView toView(WaterReferenceProfile p) {
        return new ProfileView(p.id().value(), p.isGlobal(), p.name(), p.region(), p.edition(), p.ions(),
                p.alkalinity(), p.hardness(), p.ph(), p.status().name(), p.sourceName(), p.chargeBalance());
    }
}
