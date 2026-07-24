package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitImportJobRequest(
        @NotBlank @Size(max = 60) String datasetVersion,
        @NotBlank @Size(max = 120) String contentType,
        @NotBlank String rawPayload) {}
