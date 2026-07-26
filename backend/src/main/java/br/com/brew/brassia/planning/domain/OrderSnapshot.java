package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot congelado na criação da OP: cópia da receita (com métricas calculadas)
 * e do perfil do equipamento no momento. Todos os campos são obrigatórios —
 * construir um snapshot sem métricas da receita é impossível, o que reforça a
 * regra "snapshot completo" (BOP-001; erro de "snapshot incompleto" nasce daqui).
 */
public record OrderSnapshot(Recipe recipe, Equipment equipment) {

    public OrderSnapshot {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(equipment, "equipment");
    }

    public record Recipe(UUID id, int version, String name, BigDecimal ogSg, BigDecimal fgSg, BigDecimal abv,
            BigDecimal ibu, BigDecimal colorEbc) {
        public Recipe {
            Objects.requireNonNull(id, "recipe id");
            Objects.requireNonNull(name, "recipe name");
            requireMetric(ogSg, "ogSg");
            requireMetric(fgSg, "fgSg");
            requireMetric(abv, "abv");
            requireMetric(ibu, "ibu");
            requireMetric(colorEbc, "colorEbc");
        }
    }

    public record Equipment(UUID id, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour) {
        public Equipment {
            Objects.requireNonNull(id, "equipment id");
            requireMetric(capacityLiters, "capacityLiters");
        }
    }

    private static void requireMetric(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("snapshot incompleto: " + field + " ausente");
        }
    }
}
