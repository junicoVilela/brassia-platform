package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RecommendRequest(
        @NotBlank String material,
        @NotBlank String soiling,
        @NotBlank String risk,
        String previousProduct) {}
