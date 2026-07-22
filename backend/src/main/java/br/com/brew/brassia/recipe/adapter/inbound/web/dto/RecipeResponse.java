package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import java.util.UUID;

public record RecipeResponse(UUID id, String name, String status) {}
