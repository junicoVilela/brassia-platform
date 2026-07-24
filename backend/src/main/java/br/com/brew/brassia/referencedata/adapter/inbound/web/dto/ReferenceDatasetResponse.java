package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceDatasetsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.RecordReferenceDatasetUseCase;
import java.time.Instant;
import java.util.UUID;

public record ReferenceDatasetResponse(
        UUID id,
        UUID sourceId,
        String version,
        String checksum,
        String status,
        String reviewStatus,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant publishedAt,
        Boolean created) {

    public static ReferenceDatasetResponse from(ListReferenceDatasetsUseCase.DatasetView v) {
        return new ReferenceDatasetResponse(v.id(), v.sourceId(), v.version(), v.checksum(), v.status(),
                v.reviewStatus(), v.effectiveFrom(), v.effectiveTo(), v.publishedAt(), null);
    }

    public static ReferenceDatasetResponse from(RecordReferenceDatasetUseCase.Result r) {
        return new ReferenceDatasetResponse(r.id(), null, null, r.checksum(), null, null, null, null, null,
                r.created());
    }

    public static ReferenceDatasetResponse from(PublishReferenceDatasetUseCase.Result r) {
        return new ReferenceDatasetResponse(r.id(), null, null, null, r.status(), null, null, null, r.publishedAt(),
                null);
    }
}
