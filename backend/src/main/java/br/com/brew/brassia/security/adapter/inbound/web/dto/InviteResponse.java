package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record InviteResponse(UUID userId, String email, String status) {}
