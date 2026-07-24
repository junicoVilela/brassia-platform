package br.com.brew.brassia.water.adapter.inbound.web.dto;

import java.util.UUID;

/** Resposta de ações do perfil de referência (criar/publicar): id opcional + status. */
public record WaterReferenceProfileActionResponse(UUID id, String status) {}
