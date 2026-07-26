package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import java.util.UUID;

/** Resposta da criação de uma entrada da agenda. */
public record ScheduleEntryResponse(UUID id, String status) {}
