package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RecordReferenceDatasetRequest(
        @NotBlank @Size(max = 60) String datasetVersion,
        @NotBlank String rawPayload,
        @NotBlank @Size(max = 160) String sourceSystem,
        @Size(max = 200) String sourceRecordId,
        @Size(max = 500) String sourceUrl,
        @NotNull Instant retrievedAt,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo) {}
