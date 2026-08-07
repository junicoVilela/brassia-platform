package br.com.brew.brassia.costing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Planejado versus real de um lote (CST-002): o que explica a diferença entre o custo que se
 * esperava e o que aconteceu.
 *
 * <p><strong>A conta fecha, e é o critério da história.</strong> Para cada insumo,
 * {@code variação de preço + variação de consumo = custo real − custo planejado}, exatamente — sem
 * resto e sem arredondamento que sobra. É por isso que a variação de preço multiplica pela
 * quantidade <em>real</em> e a de consumo pelo preço <em>planejado</em>: qualquer outra combinação
 * deixaria um pedaço da diferença sem dono, ou contaria o mesmo pedaço duas vezes — o
 * {@code double counting} que a sprint lista como risco.
 *
 * <p><strong>Insumo sem base não entra no total.</strong> Quando a ordem não separou nada daquele
 * ingrediente, não há preço planejado, e um zero ali transformaria o custo inteiro em "variação de
 * preço". Ele aparece na lista com as quantidades que se conhece e vira lacuna declarada.
 *
 * <p>Sinal: <strong>positivo é desfavorável</strong> em dinheiro — gastou-se mais do que o plano.
 * Em volume o sinal sozinho não basta (render menos é ruim, perder menos é bom), então cada
 * variação de volume diz por si se é desfavorável.
 */
public record BatchVariance(UUID batchId, String batchCode, List<MaterialVariance> materials,
        List<VolumeVariance> volumes, List<VarianceGap> gaps) {

    public BatchVariance {
        Objects.requireNonNull(batchId, "lote é obrigatório");
        Objects.requireNonNull(batchCode, "código do lote é obrigatório");
        materials = List.copyOf(materials);
        volumes = List.copyOf(volumes);
        gaps = List.copyOf(gaps);
    }

    /** Insumos com base de preço — os únicos que somam dinheiro. */
    public List<MaterialVariance> compared() {
        return materials.stream().filter(MaterialVariance::comparable).toList();
    }

    public BigDecimal plannedCost() {
        return sum(MaterialVariance::plannedCost);
    }

    public BigDecimal actualCost() {
        return sum(MaterialVariance::actualCost);
    }

    /** Quanto da diferença é preço: pagou-se mais (ou menos) pelo que se usou. */
    public BigDecimal priceVariance() {
        return sum(MaterialVariance::priceVariance);
    }

    /** Quanto da diferença é consumo: usou-se mais (ou menos) do que o plano pedia. */
    public BigDecimal consumptionVariance() {
        return sum(MaterialVariance::consumptionVariance);
    }

    public BigDecimal totalVariance() {
        return priceVariance().add(consumptionVariance());
    }

    /**
     * A conciliação, exposta em vez de só testada: as duas parcelas explicam a diferença inteira.
     *
     * <p>Um relatório de variação que não fecha é pior que nenhum — quem lê passa a desconfiar de
     * todos os números, inclusive dos certos.
     */
    public boolean reconciles() {
        return totalVariance().compareTo(actualCost().subtract(plannedCost())) == 0;
    }

    public boolean incomplete() {
        return !gaps.isEmpty();
    }

    /**
     * Soma exata, sem arredondar.
     *
     * <p>Arredondar cada parcela antes de somar faria preço + consumo deixar de bater com a
     * diferença total por um centavo — e o relatório que não fecha por um centavo é tão inútil
     * quanto o que não fecha por mil. Quem apresenta arredonda; quem soma, não.
     */
    private BigDecimal sum(java.util.function.Function<MaterialVariance, BigDecimal> part) {
        return compared().stream().map(part).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Um insumo, planejado contra real.
     *
     * @param plannedQuantity o que a receita pedia para o volume da ordem. <strong>Zero e vazio
     *                        são diferentes</strong>: zero é "a receita não pedia isto e a
     *                        brassagem usou assim mesmo", que é consumo extra; vazio é "não se
     *                        sabe o que a receita pedia", que não se compara com nada
     * @param actualQuantity  vazio enquanto o consumo não foi confirmado — não zero, pelo mesmo
     *                        motivo: o lote pode estar na panela
     * @param plannedUnitCost preço médio dos lotes que a ordem separou; vazio quando ela não
     *                        separou nada, e aí não há o que comparar em dinheiro
     */
    public record MaterialVariance(UUID ingredientId, String name, String unit,
            BigDecimal plannedQuantity, BigDecimal actualQuantity, BigDecimal plannedUnitCost,
            BigDecimal actualUnitCost) {

        public MaterialVariance {
            Objects.requireNonNull(ingredientId, "ingrediente é obrigatório");
            Objects.requireNonNull(name, "nome é obrigatório");
            Objects.requireNonNull(unit, "unidade é obrigatória");
        }

        /** Só entra no dinheiro o insumo cujos quatro números existem. */
        public boolean comparable() {
            return plannedQuantity != null && actualQuantity != null && plannedUnitCost != null
                    && actualUnitCost != null;
        }

        public BigDecimal quantityVariance() {
            return plannedQuantity == null || actualQuantity == null ? null
                    : actualQuantity.subtract(plannedQuantity);
        }

        public BigDecimal plannedCost() {
            return plannedQuantity == null || plannedUnitCost == null ? null
                    : plannedQuantity.multiply(plannedUnitCost);
        }

        public BigDecimal actualCost() {
            return actualQuantity == null || actualUnitCost == null ? null
                    : actualQuantity.multiply(actualUnitCost);
        }

        /** {@code (real − planejado) × preço planejado} — o efeito de ter usado outra quantidade. */
        public BigDecimal consumptionVariance() {
            return comparable() ? quantityVariance().multiply(plannedUnitCost) : null;
        }

        /** {@code (preço real − preço planejado) × quantidade real} — o efeito do preço. */
        public BigDecimal priceVariance() {
            return comparable() ? actualUnitCost.subtract(plannedUnitCost).multiply(actualQuantity) : null;
        }

        /** O que este insumo explica da diferença total. */
        public BigDecimal totalVariance() {
            return comparable() ? consumptionVariance().add(priceVariance()) : null;
        }
    }

    /** O que se compara em volume. Render menos e perder mais são coisas diferentes. */
    public enum VolumeKind {
        /** Quanto se produziu contra quanto se esperava produzir. */
        YIELD,
        /** Quanto se perdeu. */
        LOSS
    }

    /**
     * Uma comparação de volume, em litros.
     *
     * @param planned vazio quando ninguém definiu o esperado — a perda da transferência é assim
     *                hoje, e um "planejado = 0" faria toda perda parecer desvio
     */
    public record VolumeVariance(VolumeKind kind, String what, BigDecimal planned, BigDecimal actual) {

        public VolumeVariance {
            Objects.requireNonNull(kind, "tipo é obrigatório");
            Objects.requireNonNull(what, "descrição é obrigatória");
            Objects.requireNonNull(actual, "valor real é obrigatório");
        }

        public boolean comparable() {
            return planned != null;
        }

        public BigDecimal variance() {
            return planned == null ? null : actual.subtract(planned);
        }

        /** Percentual sobre o planejado; vazio sem base ou quando o planejado é zero. */
        public BigDecimal variancePercent() {
            if (planned == null || planned.signum() == 0) {
                return null;
            }
            return variance()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(planned, 2, RoundingMode.HALF_UP);
        }

        /** Render menos que o planejado é ruim; perder mais que o planejado também. */
        public boolean unfavorable() {
            var variance = variance();
            if (variance == null) {
                return false;
            }
            return kind == VolumeKind.YIELD ? variance.signum() < 0 : variance.signum() > 0;
        }
    }

    /** O que não deu para comparar, e por quê. Sem isso, o silêncio viraria "não houve variação". */
    public record VarianceGap(String what, String reason) {

        public VarianceGap {
            Objects.requireNonNull(what, "assunto da lacuna é obrigatório");
            Objects.requireNonNull(reason, "motivo da lacuna é obrigatório");
        }
    }
}
