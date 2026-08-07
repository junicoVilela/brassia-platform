package br.com.brew.brassia.costing.adapter.inbound.web.dto;

import br.com.brew.brassia.costing.domain.BatchVariance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Contratos do planejado versus real (CST-002). */
public final class VarianceDtos {

    /** Duas casas para dinheiro, quatro para quantidade: é como a fábrica lê os dois. */
    private static final int MONEY = 2;
    private static final int QUANTITY = 4;

    private VarianceDtos() {
    }

    /**
     * @param reconciles verdadeiro quando preço + consumo explicam a diferença inteira. Vai no
     *                   contrato de propósito: quem consome tem como conferir que o relatório
     *                   fecha, em vez de confiar
     */
    public record VarianceView(UUID batchId, String batchCode, BigDecimal plannedCost,
            BigDecimal actualCost, BigDecimal priceVariance, BigDecimal consumptionVariance,
            BigDecimal totalVariance, boolean reconciles, boolean incomplete,
            List<MaterialView> materials, List<VolumeView> volumes, List<GapView> gaps) {

        public static VarianceView from(BatchVariance variance) {
            return new VarianceView(variance.batchId(), variance.batchCode(),
                    money(variance.plannedCost()), money(variance.actualCost()),
                    money(variance.priceVariance()), money(variance.consumptionVariance()),
                    money(variance.totalVariance()), variance.reconciles(), variance.incomplete(),
                    variance.materials().stream().map(MaterialView::from).toList(),
                    variance.volumes().stream().map(VolumeView::from).toList(),
                    variance.gaps().stream().map(GapView::from).toList());
        }
    }

    /**
     * @param comparable falso quando falta algum dos quatro números; os campos nulos dizem qual
     * @param plannedQuantity nulo é "não se sabe o que a receita pedia"; zero é "não pedia nada"
     */
    public record MaterialView(UUID ingredientId, String name, String unit, BigDecimal plannedQuantity,
            BigDecimal actualQuantity, BigDecimal quantityVariance, BigDecimal plannedUnitCost,
            BigDecimal actualUnitCost, BigDecimal plannedCost, BigDecimal actualCost,
            BigDecimal priceVariance, BigDecimal consumptionVariance, BigDecimal totalVariance,
            boolean comparable) {

        static MaterialView from(BatchVariance.MaterialVariance material) {
            return new MaterialView(material.ingredientId(), material.name(), material.unit(),
                    quantity(material.plannedQuantity()), quantity(material.actualQuantity()),
                    quantity(material.quantityVariance()), money(material.plannedUnitCost()),
                    money(material.actualUnitCost()), money(material.plannedCost()),
                    money(material.actualCost()), money(material.priceVariance()),
                    money(material.consumptionVariance()), money(material.totalVariance()),
                    material.comparable());
        }
    }

    /**
     * @param planned      nulo quando ninguém definiu o esperado — perda é assim hoje (CST-002-A)
     * @param unfavorable  render menos ou perder mais; o sinal sozinho não diz isso
     */
    public record VolumeView(String kind, String what, BigDecimal planned, BigDecimal actual,
            BigDecimal variance, BigDecimal variancePercent, boolean comparable, boolean unfavorable) {

        static VolumeView from(BatchVariance.VolumeVariance volume) {
            return new VolumeView(volume.kind().name(), volume.what(), quantity(volume.planned()),
                    quantity(volume.actual()), quantity(volume.variance()), volume.variancePercent(),
                    volume.comparable(), volume.unfavorable());
        }
    }

    public record GapView(String what, String reason) {

        static GapView from(BatchVariance.VarianceGap gap) {
            return new GapView(gap.what(), gap.reason());
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return value == null ? null : value.setScale(QUANTITY, RoundingMode.HALF_UP);
    }
}
