package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateServiceAccountRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 160) String name) {}
