package br.com.brew.brassia.recipe.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloneRecipeRequest(@NotBlank @Size(max = 120) String name) {}
