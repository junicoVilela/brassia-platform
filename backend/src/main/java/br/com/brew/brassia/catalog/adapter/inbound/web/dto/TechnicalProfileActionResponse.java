package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import java.util.UUID;

/** Resposta de ações do perfil técnico (criar/publicar): id opcional + status. */
public record TechnicalProfileActionResponse(UUID id, String status) {}
