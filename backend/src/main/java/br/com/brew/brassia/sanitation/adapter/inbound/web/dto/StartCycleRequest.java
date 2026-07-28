package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartCycleRequest(
        @NotBlank String procedureCode,
        @NotNull UUID equipmentId) {}
