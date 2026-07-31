package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateProfileRequest(
        @NotBlank String name,
        @NotEmpty List<@Valid StageDto> stages,
        @Valid StabilityDto stability) {}
