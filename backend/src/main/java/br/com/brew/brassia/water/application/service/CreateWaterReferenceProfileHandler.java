package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.CreateWaterReferenceProfileUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterReferenceProfileRepository;
import br.com.brew.brassia.water.domain.WaterReferenceProfile;
import java.util.Map;
import java.util.Objects;

public final class CreateWaterReferenceProfileHandler implements CreateWaterReferenceProfileUseCase {

    private final WaterReferenceProfileRepository profiles;
    private final AuditTrail audit;

    public CreateWaterReferenceProfileHandler(WaterReferenceProfileRepository profiles, AuditTrail audit) {
        this.profiles = Objects.requireNonNull(profiles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (profiles.existsByNameEdition(command.breweryId(), command.name(), command.edition())) {
            throw new IllegalStateException("já existe um perfil de referência com esse nome/edição no escopo");
        }
        var profile = WaterReferenceProfile.draft(command.breweryId(), command.name(), command.region(),
                command.edition(), command.ions(), command.alkalinity(), command.hardness(), command.ph(),
                command.sourceId(), command.sourceName());
        profiles.insert(profile);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.reference_profile.create",
                "water_reference_profile", profile.id().value().toString(),
                Map.of("name", profile.name(), "edition", profile.edition())));

        return new Result(profile.id().value(), profile.status().name());
    }
}
