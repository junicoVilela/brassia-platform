package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Metas cervejeiras calculadas e persistidas de uma receita (REC-003). */
public record RecipeMetrics(
        UUID recipeId,
        UUID breweryId,
        BigDecimal ogPoints,
        BigDecimal ogSg,
        BigDecimal fgPoints,
        BigDecimal fgSg,
        BigDecimal abv,
        BigDecimal ibu,
        BigDecimal colorEbc,
        BigDecimal attenuationPercent,
        String method,
        int version) {}
