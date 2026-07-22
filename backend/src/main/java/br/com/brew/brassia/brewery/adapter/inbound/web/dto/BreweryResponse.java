package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import java.util.UUID;

public record BreweryResponse(UUID id, String code, String name, String timezone) {}
