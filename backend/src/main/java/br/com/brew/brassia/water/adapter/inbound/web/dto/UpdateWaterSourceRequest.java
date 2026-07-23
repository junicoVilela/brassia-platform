package br.com.brew.brassia.water.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateWaterSourceRequest(@NotBlank String name, long version) {}
