package br.com.brew.brassia.recipe;

import java.time.Instant;
import java.util.UUID;

public record RecipePublished(UUID breweryId, UUID recipeId, int version, Instant occurredAt) {}
