package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import java.util.UUID;

/**
 * Responsável pela liberação. Opcional no contrato de propósito: a ausência vira
 * um bloqueio listado (409), não um erro de validação (400).
 */
public record ReleaseBrewOrderRequest(UUID assignedUserId) {}
