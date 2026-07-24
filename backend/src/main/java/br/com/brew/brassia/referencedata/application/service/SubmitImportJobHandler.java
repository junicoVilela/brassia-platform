package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.SubmitImportJobUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ImportJobRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.Checksum;
import br.com.brew.brassia.referencedata.domain.Checksums;
import br.com.brew.brassia.referencedata.domain.ImportJob;
import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class SubmitImportJobHandler implements SubmitImportJobUseCase {

    private final ReferenceSourceRepository sources;
    private final ReferenceDatasetRepository datasets;
    private final ImportJobRepository jobs;
    private final ImportPayloadValidator validator;
    private final AuditTrail audit;

    public SubmitImportJobHandler(ReferenceSourceRepository sources, ReferenceDatasetRepository datasets,
            ImportJobRepository jobs, ImportPayloadValidator validator, AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.datasets = Objects.requireNonNull(datasets);
        this.jobs = Objects.requireNonNull(jobs);
        this.validator = Objects.requireNonNull(validator);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = sources.findVisible(command.breweryId(), command.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));

        long sizeBytes = command.rawPayload() == null ? 0
                : command.rawPayload().getBytes(StandardCharsets.UTF_8).length;
        Checksum checksum = Checksums.sha256(command.rawPayload());
        var job = ImportJob.receive(source.id(), command.breweryId(), command.datasetVersion(),
                command.contentType(), sizeBytes, checksum, command.rawPayload());

        var issues = new ArrayList<ValidationIssue>(validator.validate(command.contentType(), command.rawPayload(),
                sizeBytes));
        datasets.findByChecksum(source.id().value(), checksum.value()).ifPresent(existing ->
                issues.add(ValidationIssue.warning("duplicate", "conteúdo idêntico já publicado nesta fonte")));

        job.startValidation();
        job.recordValidation(issues); // erros → FAILED; caso contrário → REVIEW_REQUIRED
        jobs.insert(job);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.import.submit",
                "import_job", job.id().value().toString(),
                Map.of("source", source.id().value().toString(), "status", job.status().name())));

        return new Result(job.id().value(), job.status().name(), job.issues());
    }
}
