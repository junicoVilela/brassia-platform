package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterProfile;
import br.com.brew.brassia.water.domain.WaterProfileCode;
import br.com.brew.brassia.water.domain.WaterProfileName;
import java.util.Map;
import java.util.Objects;

public final class RegisterWaterProfileHandler implements RegisterWaterProfileUseCase {
    private final WaterProfileRepository repository;
    private final AuditTrail audit;

    public RegisterWaterProfileHandler(WaterProfileRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var code = new WaterProfileCode(command.code());
        var name = new WaterProfileName(command.name());
        var targets = new IonProfile(command.calcium(), command.magnesium(), command.sodium(),
                command.sulfate(), command.chloride(), command.bicarbonate());

        if (repository.existsByCode(command.breweryId(), code.value())) {
            throw new IllegalStateException("código de perfil já existe nesta cervejaria");
        }

        var profile = WaterProfile.register(command.breweryId(), code, name, targets);
        repository.insert(profile);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.profile.register",
                "water_profile", profile.id().value().toString(), Map.of("code", code.value())));

        return new Result(profile.id().value(), code.value(), name.value(), targets.calcium(),
                targets.magnesium(), targets.sodium(), targets.sulfate(), targets.chloride(),
                targets.bicarbonate(), profile.active(), profile.version());
    }
}
