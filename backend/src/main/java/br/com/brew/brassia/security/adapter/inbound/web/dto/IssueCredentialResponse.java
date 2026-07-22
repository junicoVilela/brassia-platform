package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record IssueCredentialResponse(UUID credentialId, String rawKey, String keyPrefix) {}
