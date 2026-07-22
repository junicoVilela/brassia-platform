package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.UUID;

public record BreweryView(UUID id, String code, String name) {}
