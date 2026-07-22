package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id, String email, String displayName, String status, String emailVerifiedAt) {}
