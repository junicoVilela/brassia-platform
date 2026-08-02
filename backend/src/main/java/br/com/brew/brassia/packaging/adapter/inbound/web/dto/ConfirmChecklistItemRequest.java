package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmChecklistItemRequest(@NotBlank String item) {}
