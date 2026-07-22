package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record AcceptInvitationResponse(UUID userId, String status) {}
