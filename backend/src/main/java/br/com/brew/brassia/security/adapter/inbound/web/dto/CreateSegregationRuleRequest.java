package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSegregationRuleRequest(
        @NotBlank String leftPermissionCode,
        @NotBlank String rightPermissionCode,
        @NotBlank String reason) {}
