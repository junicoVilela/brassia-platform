package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateProcedureRequest(
        @NotBlank String name,
        @NotEmpty List<ProcedureStepDto> steps) {}
