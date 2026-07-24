package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.domain.StyleAuthority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateStyleSetRequest(
        @NotNull UUID sourceId,
        @NotNull StyleAuthority authority,
        @NotBlank @Size(max = 40) String edition,
        @NotBlank @Size(max = 16) String language,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo,
        @Size(max = 300) String attribution,
        @NotEmpty @Valid List<StyleRequest> styles) {}
