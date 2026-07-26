package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explosão da receita em necessidade de materiais (PLN-002): escala cada item pelo
 * volume alvo, aplica uma perda percentual, converte para a unidade canônica e
 * agrega por ingrediente. É cálculo puro — não reserva estoque nem persiste.
 *
 * <p>Fórmula: {@code necessario_i = qtd_i × (volumeAlvo / volumeReceita) × (1 + perda%)}.
 * A comparação com estoque disponível/faltas fica para o módulo de inventário
 * (Sprint 06); aqui só a necessidade.
 */
public final class MaterialExplosion {

    private static final int SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MaterialExplosion() {
    }

    /** Um item da composição da receita (referência ao ingrediente do catálogo). */
    public record Component(UUID ingredientId, BigDecimal quantity, String unit) {
        public Component {
            Objects.requireNonNull(ingredientId, "ingredientId");
            Objects.requireNonNull(unit, "unit");
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantidade deve ser positiva");
            }
        }
    }

    /** Necessidade agregada de um ingrediente, na unidade canônica. */
    public record Requirement(UUID ingredientId, BigDecimal requiredQuantity, String unit) {}

    public static List<Requirement> explode(List<Component> items, BigDecimal recipeVolumeLiters,
            BigDecimal targetVolumeLiters, BigDecimal lossPercent) {
        if (recipeVolumeLiters == null || recipeVolumeLiters.signum() <= 0) {
            throw new IllegalArgumentException("volume da receita deve ser positivo");
        }
        if (targetVolumeLiters == null || targetVolumeLiters.signum() <= 0) {
            throw new IllegalArgumentException("volume alvo deve ser positivo");
        }
        var loss = lossPercent == null ? BigDecimal.ZERO : lossPercent;
        if (loss.signum() < 0) {
            throw new IllegalArgumentException("perda não pode ser negativa");
        }

        var factor = targetVolumeLiters
                .divide(recipeVolumeLiters, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.add(loss.divide(HUNDRED, 10, RoundingMode.HALF_UP)));

        // Agrega por ingrediente + unidade canônica, preservando a ordem de aparição.
        var byKey = new LinkedHashMap<String, Requirement>();
        for (var item : items) {
            var canonicalUnit = MaterialUnit.canonicalOf(item.unit());
            var amount = MaterialUnit.toCanonical(item.quantity(), item.unit()).multiply(factor);
            var key = item.ingredientId() + "|" + canonicalUnit;
            var current = byKey.get(key);
            var total = current == null ? amount : current.requiredQuantity().add(amount);
            byKey.put(key, new Requirement(item.ingredientId(), total, canonicalUnit));
        }

        var result = new ArrayList<Requirement>(byKey.size());
        for (var requirement : byKey.values()) {
            result.add(new Requirement(requirement.ingredientId(),
                    requirement.requiredQuantity().setScale(SCALE, RoundingMode.HALF_UP), requirement.unit()));
        }
        return List.copyOf(result);
    }
}
