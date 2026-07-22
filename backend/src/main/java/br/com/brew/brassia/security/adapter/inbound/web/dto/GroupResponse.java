package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        UUID breweryId,
        boolean systemGroup,
        boolean active,
        long version,
        List<String> permissions) {}
