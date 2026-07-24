package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceDatasetsUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceSourceRepository;
import br.com.brew.brassia.referencedata.domain.ReferenceDataset;
import java.util.List;
import java.util.Objects;

public final class ListReferenceDatasetsHandler implements ListReferenceDatasetsUseCase {

    private final ReferenceSourceRepository sources;
    private final ReferenceDatasetRepository datasets;

    public ListReferenceDatasetsHandler(ReferenceSourceRepository sources, ReferenceDatasetRepository datasets) {
        this.sources = Objects.requireNonNull(sources);
        this.datasets = Objects.requireNonNull(datasets);
    }

    @Override
    public List<DatasetView> handle(Query query) {
        // Guarda de tenant: só lista datasets de uma fonte visível à cervejaria.
        sources.findVisible(query.breweryId(), query.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("fonte inexistente ou fora do escopo"));
        return datasets.findBySource(query.sourceId()).stream()
                .map(ListReferenceDatasetsHandler::toView)
                .toList();
    }

    private static DatasetView toView(ReferenceDataset d) {
        return new DatasetView(d.id().value(), d.sourceId().value(), d.version().value(), d.checksum().value(),
                d.status().name(), d.reviewStatus().name(), d.effectiveFrom(), d.effectiveTo(), d.publishedAt());
    }
}
