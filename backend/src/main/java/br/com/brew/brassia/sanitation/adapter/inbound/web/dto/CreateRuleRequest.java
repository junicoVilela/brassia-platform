package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleRequest(
        @NotBlank String material,
        @NotBlank String soiling,
        @NotBlank String risk,
        String previousProduct,
        String procedureCode,
        @NotBlank String method,
        String alternative,
        String restriction) {}
