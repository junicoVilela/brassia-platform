package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record InterruptCycleRequest(@NotBlank String reason) {}
