package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.PublishProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import java.util.Map;
import java.util.Objects;

/** Publica um perfil (FER-001): DRAFT → PUBLISHED, guardado pelo estado (congela a versão). */
public final class PublishProfileHandler implements PublishProfileUseCase {

    private final ProfileRepository repository;
    private final AuditTrail audit;

    public PublishProfileHandler(ProfileRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var profile = repository.findById(command.breweryId(), command.profileId())
                .orElseThrow(() -> new IllegalArgumentException("perfil inexistente"));
        if (profile.stages().isEmpty()) {
            throw new IllegalStateException("perfil sem estágios não pode ser publicado");
        }

        if (!repository.markPublished(command.breweryId(), command.profileId())) {
            throw new IllegalStateException("perfil não está em rascunho");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.profile.publish",
                "fermentation.profile", profile.id().value().toString(),
                Map.of("code", profile.code(), "version", String.valueOf(profile.version()))));
    }
}
