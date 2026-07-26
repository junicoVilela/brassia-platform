package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo do cancelamento (obrigatório). */
public record CancelBrewOrderRequest(@NotBlank @Size(max = 500) String reason) {}
