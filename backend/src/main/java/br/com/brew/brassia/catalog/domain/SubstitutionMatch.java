package br.com.brew.brassia.catalog.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Alternativa ranqueada para substituição, com score, confiança, fonte e o
 * comparativo por propriedade (similaridades e diferenças).
 */
public record SubstitutionMatch(UUID ingredientId, String code, String name, String sourceName, BigDecimal score,
        String confidence, List<PropertyComparison> comparisons) {}
