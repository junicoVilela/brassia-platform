package br.com.brew.brassia.referencedata.application.port.inbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListReferenceDatasetsUseCase {

    List<DatasetView> handle(Query query);

    record Query(UUID breweryId, UUID sourceId) {}

    record DatasetView(UUID id, UUID sourceId, String version, String checksum, String status, String reviewStatus,
            Instant effectiveFrom, Instant effectiveTo, Instant publishedAt) {}
}
