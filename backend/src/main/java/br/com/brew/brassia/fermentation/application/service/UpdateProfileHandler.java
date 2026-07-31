package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.StabilityInput;
import br.com.brew.brassia.fermentation.application.port.inbound.UpdateProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import java.util.Map;
import java.util.Objects;

/** Atualiza um perfil em rascunho (FER-001); publicado é imutável (domínio → 409). */
public final class UpdateProfileHandler implements UpdateProfileUseCase {

    private final ProfileRepository repository;
    private final AuditTrail audit;

    public UpdateProfileHandler(ProfileRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var profile = repository.findById(command.breweryId(), command.profileId())
                .orElseThrow(() -> new IllegalArgumentException("perfil inexistente"));

        profile.update(command.name(), ProfileStages.from(command.stages()),
                StabilityInput.toPolicy(command.stability()));
        repository.update(profile);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.profile.update",
                "fermentation.profile", profile.id().value().toString(),
                Map.of("code", profile.code(), "version", String.valueOf(profile.version()))));
    }
}
