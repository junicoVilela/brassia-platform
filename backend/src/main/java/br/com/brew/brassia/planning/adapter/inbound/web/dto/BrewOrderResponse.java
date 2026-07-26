package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import java.util.UUID;

/** Resposta da criação de uma OP. */
public record BrewOrderResponse(UUID id, String code, String status) {}
