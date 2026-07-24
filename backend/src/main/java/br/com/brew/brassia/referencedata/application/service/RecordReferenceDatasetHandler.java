package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.RecordReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.Checksum;
import br.com.brew.brassia.referencedata.domain.Checksums;
import br.com.brew.brassia.referencedata.domain.DatasetVersion;
import br.com.brew.brassia.referencedata.domain.Provenance;
import br.com.brew.brassia.referencedata.domain.ReferenceDataset;
import java.util.Map;
import java.util.Objects;

public final class RecordReferenceDatasetHandler implements RecordReferenceDatasetUseCase {

    private final ReferenceSourceRepository sources;
    private final ReferenceDatasetRepository datasets;
    private final AuditTrail audit;

    public RecordReferenceDatasetHandler(ReferenceSourceRepository sources, ReferenceDatasetRepository datasets,
            AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.datasets = Objects.requireNonNull(datasets);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var source = sources.findVisible(command.breweryId(), command.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));
        Checksum checksum = Checksums.sha256(command.rawPayload());

        var existing = datasets.findByChecksum(source.id().value(), checksum.value());
        if (existing.isPresent()) {
            return new Result(existing.get().id().value(), checksum.value(), false);
        }

        var dataset = ReferenceDataset.draft(source.id(), new DatasetVersion(command.datasetVersion()), checksum,
                new Provenance(command.sourceSystem(), command.sourceRecordId(), command.sourceUrl(),
                        command.retrievedAt()),
                command.rawPayload(), command.effectiveFrom(), command.effectiveTo());
        datasets.insert(dataset);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.dataset.record",
                "reference_dataset", dataset.id().value().toString(),
                Map.of("source", source.id().value().toString(), "version", command.datasetVersion())));

        return new Result(dataset.id().value(), checksum.value(), true);
    }
}
