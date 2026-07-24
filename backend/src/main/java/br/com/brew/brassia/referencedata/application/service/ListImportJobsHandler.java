package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.ListImportJobsUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ImportJobRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.ImportJob;
import java.util.List;
import java.util.Objects;

public final class ListImportJobsHandler implements ListImportJobsUseCase {

    private final ReferenceSourceRepository sources;
    private final ImportJobRepository jobs;

    public ListImportJobsHandler(ReferenceSourceRepository sources, ImportJobRepository jobs) {
        this.sources = Objects.requireNonNull(sources);
        this.jobs = Objects.requireNonNull(jobs);
    }

    @Override
    public List<JobView> handle(Query query) {
        sources.findVisible(query.breweryId(), query.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));
        return jobs.findBySource(query.sourceId()).stream().map(ListImportJobsHandler::toView).toList();
    }

    private static JobView toView(ImportJob j) {
        return new JobView(j.id().value(), j.datasetVersion(), j.contentType(), j.sizeBytes(), j.status().name(),
                j.publishedDatasetId() == null ? null : j.publishedDatasetId().value(), j.issues());
    }
}
