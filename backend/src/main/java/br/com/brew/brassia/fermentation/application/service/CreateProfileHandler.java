package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.CreateProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.StabilityInput;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.Map;
import java.util.Objects;

/**
 * Cria um perfil de fermentação em rascunho (FER-001). Versionamento por código: código
 * novo → versão 1; código com versão publicada → próxima versão; código com rascunho
 * aberto → conflito (só um rascunho por vez).
 */
public final class CreateProfileHandler implements CreateProfileUseCase {

    private final ProfileRepository repository;
    private final AuditTrail audit;

    public CreateProfileHandler(ProfileRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var latest = repository.findLatestByCode(command.breweryId(), command.code());
        int version = 1;
        if (latest.isPresent()) {
            if (latest.get().draftStatus()) {
                throw new IllegalStateException("já existe um rascunho aberto para este código");
            }
            version = latest.get().version() + 1;
        }

        var profile = FermentationProfile.draft(command.breweryId(), command.code(), command.name(), version,
                ProfileStages.from(command.stages()), StabilityInput.toPolicy(command.stability()));
        repository.insert(profile);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.profile.create",
                "fermentation.profile", profile.id().value().toString(),
                Map.of("code", profile.code(), "version", String.valueOf(version))));

        return new Result(profile.id().value(), version);
    }
}
