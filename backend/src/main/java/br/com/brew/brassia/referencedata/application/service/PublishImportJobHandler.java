package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.ReferenceDatasetPublished;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishImportJobUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ImportJobRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDataEventPublisher;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.DatasetStatus;
import br.com.brew.brassia.referencedata.domain.DatasetVersion;
import br.com.brew.brassia.referencedata.domain.Provenance;
import br.com.brew.brassia.referencedata.domain.ReferenceDataset;
import br.com.brew.brassia.referencedata.domain.ReferenceDatasetId;
import br.com.brew.brassia.referencedata.domain.ReviewStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class PublishImportJobHandler implements PublishImportJobUseCase {

    private final ReferenceSourceRepository sources;
    private final ReferenceDatasetRepository datasets;
    private final ImportJobRepository jobs;
    private final ReferenceDataEventPublisher events;
    private final AuditTrail audit;

    public PublishImportJobHandler(ReferenceSourceRepository sources, ReferenceDatasetRepository datasets,
            ImportJobRepository jobs, ReferenceDataEventPublisher events, AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.datasets = Objects.requireNonNull(datasets);
        this.jobs = Objects.requireNonNull(jobs);
        this.events = Objects.requireNonNull(events);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var job = jobs.findById(command.jobId())
                .orElseThrow(() -> new IllegalArgumentException("job inexistente"));
        // Guarda de tenant: a fonte precisa ser visível (própria ou global).
        var source = sources.findVisible(command.breweryId(), job.sourceId().value())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));

        // Conteúdo idêntico já publicado nesta fonte não republica (idempotência forte).
        if (datasets.findByChecksum(source.id().value(), job.checksum().value()).isPresent()) {
            throw new IllegalStateException("conteúdo idêntico já publicado nesta fonte");
        }

        var when = Instant.now();
        var datasetId = ReferenceDatasetId.newId();
        // Gate de licença + máquina de estado (IllegalStateException = 409).
        job.publish(source.permissionStatus(), datasetId, when);

        var dataset = ReferenceDataset.reconstitute(datasetId, job.sourceId(),
                new DatasetVersion(job.datasetVersion()), job.checksum(),
                new Provenance(source.owner(), null, source.url(), when), job.rawPayload(), when, null,
                DatasetStatus.PUBLISHED, ReviewStatus.APPROVED, when, 1);
        datasets.insert(dataset);
        if (!jobs.markPublished(job.id().value(), datasetId.value(), job.optimisticVersion())) {
            throw new IllegalStateException("job não está em revisão ou foi alterado concorrentemente");
        }

        events.publish(new ReferenceDatasetPublished(command.breweryId(), datasetId.value(), source.id().value(),
                when));
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.import.publish",
                "import_job", job.id().value().toString(),
                Map.of("source", source.id().value().toString(), "dataset", datasetId.value().toString())));

        return new Result(job.id().value(), job.status().name(), datasetId.value());
    }
}
