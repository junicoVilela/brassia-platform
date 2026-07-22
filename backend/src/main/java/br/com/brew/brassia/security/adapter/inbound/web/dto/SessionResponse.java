package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SessionResponse(
        UUID userId,
        String displayName,
        BreweryView activeBrewery,
        List<BreweryView> accessibleBreweries,
        Set<String> permissions) {}
