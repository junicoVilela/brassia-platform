package br.com.brew.brassia.production.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;

public record PreviewCorrectionRequest(@NotBlank String calculator, Map<String, BigDecimal> inputs) {}
