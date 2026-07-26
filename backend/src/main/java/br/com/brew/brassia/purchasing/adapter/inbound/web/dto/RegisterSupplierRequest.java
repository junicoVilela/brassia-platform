package br.com.brew.brassia.purchasing.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterSupplierRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 40) String code) {}
