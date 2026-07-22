package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.List;
import java.util.UUID;

public record ServiceAccountResponse(
        UUID id, UUID breweryId, String code, boolean active, List<String> credentialPrefixes) {}
