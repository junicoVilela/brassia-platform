package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ApplyCorrectionRequest(
        @NotBlank String calculator,
        Map<String, BigDecimal> inputs,
        UUID sourceMeasurementId,
        String note,
        BigDecimal realizedValue) {}
