package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record AccountStatusResponse(UUID userId, String status) {}
