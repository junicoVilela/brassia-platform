package br.com.brew.brassia.water.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterWaterSourceRequest(@NotBlank String code, @NotBlank String name) {}
