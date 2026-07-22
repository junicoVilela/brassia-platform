package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateAlertStatusRequest(
        @NotBlank @Pattern(regexp = "ACKNOWLEDGED|RESOLVED") String status) {}
