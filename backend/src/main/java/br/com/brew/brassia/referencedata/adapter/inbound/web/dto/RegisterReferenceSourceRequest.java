package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.domain.PermissionStatus;
import br.com.brew.brassia.referencedata.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterReferenceSourceRequest(
        @NotNull SourceType type,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 160) String owner,
        @Size(max = 500) String url,
        @NotBlank @Size(max = 160) String licenseName,
        @NotNull PermissionStatus permissionStatus,
        @Size(max = 300) String attribution,
        @Size(max = 60) String reviewFrequency,
        @Size(max = 160) String responsible) {}
