package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.ReferenceDatasetPublished;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDataEventPublisher;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class PublishReferenceDatasetHandler implements PublishReferenceDatasetUseCase {

    private final ReferenceSourceRepository sources;
    private final ReferenceDatasetRepository datasets;
    private final ReferenceDataEventPublisher events;
    private final AuditTrail audit;

    public PublishReferenceDatasetHandler(ReferenceSourceRepository sources, ReferenceDatasetRepository datasets,
            ReferenceDataEventPublisher events, AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.datasets = Objects.requireNonNull(datasets);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var dataset = datasets.findById(command.datasetId())
                .orElseThrow(() -> new IllegalArgumentException("dataset inexistente"));
        // Guarda de tenant: a fonte precisa ser visível (própria ou global).
        var source = sources.findVisible(command.breweryId(), dataset.sourceId().value())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));

        var when = Instant.now();
        // Gate de licença (IllegalStateException = 409) antes de tocar o banco.
        dataset.publish(source.permissionStatus(), when);
        if (!datasets.markPublished(dataset.id().value(), when, dataset.optimisticVersion())) {
            throw new IllegalStateException("dataset não está em rascunho ou foi alterado concorrentemente");
        }

        events.publish(new ReferenceDatasetPublished(command.breweryId(), dataset.id().value(),
                source.id().value(), when));
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.dataset.publish",
                "reference_dataset", dataset.id().value().toString(),
                Map.of("source", source.id().value().toString(), "permission", source.permissionStatus().name())));

        return new Result(dataset.id().value(), dataset.status().name(), when);
    }
}
