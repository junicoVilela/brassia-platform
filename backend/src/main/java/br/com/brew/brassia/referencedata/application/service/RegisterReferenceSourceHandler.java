package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.RegisterReferenceSourceUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.LicenseInfo;
import br.com.brew.brassia.referencedata.domain.ReferenceSource;
import java.util.Map;
import java.util.Objects;

public final class RegisterReferenceSourceHandler implements RegisterReferenceSourceUseCase {

    private final ReferenceSourceRepository repository;
    private final AuditTrail audit;

    public RegisterReferenceSourceHandler(ReferenceSourceRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (repository.existsByName(command.breweryId(), command.name())) {
            throw new IllegalStateException("já existe uma fonte com esse nome no escopo");
        }
        var source = ReferenceSource.register(command.breweryId(), command.type(), command.name(), command.owner(),
                command.url(),
                new LicenseInfo(command.licenseName(), command.permissionStatus(), command.attribution()),
                command.reviewFrequency(), command.responsible());
        repository.insert(source);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.source.register",
                "reference_source", source.id().value().toString(),
                Map.of("type", source.type().name(), "permission", source.permissionStatus().name())));

        return new Result(source.id().value());
    }
}
