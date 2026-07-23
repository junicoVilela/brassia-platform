package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterProfileName;
import java.util.Map;
import java.util.Objects;

public final class UpdateWaterProfileHandler implements UpdateWaterProfileUseCase {
    private final WaterProfileRepository repository;
    private final AuditTrail audit;

    public UpdateWaterProfileHandler(WaterProfileRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var profile = repository.findById(command.breweryId(), command.profileId())
                .orElseThrow(() -> new IllegalArgumentException("perfil inexistente"));
        var targets = new IonProfile(command.calcium(), command.magnesium(), command.sodium(),
                command.sulfate(), command.chloride(), command.bicarbonate());
        profile.update(new WaterProfileName(command.name()), targets);
        if (!repository.update(profile, command.version())) {
            throw new IllegalStateException("versão do perfil divergiu");
        }
        var refreshed = repository.findById(command.breweryId(), command.profileId()).orElseThrow();
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.profile.update",
                "water_profile", refreshed.id().value().toString(), Map.of("code", refreshed.code().value())));

        var t = refreshed.targets();
        return new Result(refreshed.id().value(), refreshed.code().value(), refreshed.name().value(),
                t.calcium(), t.magnesium(), t.sodium(), t.sulfate(), t.chloride(), t.bicarbonate(),
                refreshed.active(), refreshed.version());
    }
}
