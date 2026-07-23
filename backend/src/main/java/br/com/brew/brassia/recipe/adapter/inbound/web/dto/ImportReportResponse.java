package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import java.util.List;
import java.util.UUID;

/** Relatório de compatibilidade da importação (REC-006). */
public record ImportReportResponse(UUID id, String name, String status, List<String> unknownFields) {}
