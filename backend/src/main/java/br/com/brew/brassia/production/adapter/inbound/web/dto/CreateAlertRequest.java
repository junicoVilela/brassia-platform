package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record CreateAlertRequest(
        @NotBlank String kind,
        @NotBlank String message,
        Instant plannedAt,
        Instant occurredAt) {}
