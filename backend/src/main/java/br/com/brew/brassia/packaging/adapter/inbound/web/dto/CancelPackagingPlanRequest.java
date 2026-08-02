package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPackagingPlanRequest(@NotBlank @Size(max = 200) String reason) {}
