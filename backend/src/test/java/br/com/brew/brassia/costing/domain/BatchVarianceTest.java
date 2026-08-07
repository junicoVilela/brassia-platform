package br.com.brew.brassia.costing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.costing.domain.BatchVariance.MaterialVariance;
import br.com.brew.brassia.costing.domain.BatchVariance.VarianceGap;
import br.com.brew.brassia.costing.domain.BatchVariance.VolumeKind;
import br.com.brew.brassia.costing.domain.BatchVariance.VolumeVariance;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que a variação separa, o que ela se recusa a comparar e a conta que tem de fechar (CST-002). */
class BatchVarianceTest {

    @Test
    @DisplayName("preço e consumo explicam a diferença inteira, sem sobra")
    void aContaFecha() {
        // Plano: 20 KG a 5,00 = 100,00. Real: 22 KG a 5,50 = 121,00.
        var variance = of(material("Malte", "20", "22", "5", "5.5"));

        assertThat(variance.plannedCost()).isEqualByComparingTo("100");
        assertThat(variance.actualCost()).isEqualByComparingTo("121");
        // Consumo: 2 KG a mais ao preço planejado. Preço: 0,50 a mais sobre os 22 KG usados.
        assertThat(variance.consumptionVariance()).isEqualByComparingTo("10");
        assertThat(variance.priceVariance()).isEqualByComparingTo("11");
        assertThat(variance.totalVariance()).isEqualByComparingTo("21");
        assertThat(variance.reconciles()).isTrue();
    }

    @Test
    @DisplayName("a conta fecha também com preços que não são redondos")
    void aContaFechaComDizimas() {
        var variance = of(material("Malte", "20.5", "19.3", "3.3333", "3.7777"),
                material("Lúpulo", "0.6", "0.75", "120.15", "118.9"));

        // A prova é a identidade, não os números: preço + consumo = real − planejado, exatamente.
        assertThat(variance.totalVariance())
                .isEqualByComparingTo(variance.actualCost().subtract(variance.plannedCost()));
        assertThat(variance.reconciles()).isTrue();
    }

    @Test
    @DisplayName("usar menos do que o plano é variação favorável, e o sinal diz isso")
    void economizarDaSinalNegativo() {
        var variance = of(material("Malte", "20", "18", "5", "5"));

        assertThat(variance.consumptionVariance()).isEqualByComparingTo("-10");
        assertThat(variance.priceVariance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("insumo sem preço planejado fica fora do dinheiro em vez de virar variação de preço")
    void semBaseNaoEntraNoTotal() {
        var semBase = new MaterialVariance(UUID.randomUUID(), "Lúpulo", "KG", new BigDecimal("1"),
                new BigDecimal("1"), null, new BigDecimal("200"));
        var variance = of(material("Malte", "20", "20", "5", "5"), semBase);

        assertThat(semBase.comparable()).isFalse();
        assertThat(variance.compared()).hasSize(1);
        // Os 200 do lúpulo não aparecem no real: entrariam sem par no planejado e explodiriam a
        // "variação de preço" com uma diferença que é, na verdade, falta de base.
        assertThat(variance.actualCost()).isEqualByComparingTo("100");
        assertThat(variance.reconciles()).isTrue();
    }

    @Test
    @DisplayName("plano desconhecido não é plano zero: o insumo não se compara")
    void planoDesconhecidoNaoEhZero() {
        var semPlano = new MaterialVariance(UUID.randomUUID(), "Malte", "KG", null,
                new BigDecimal("22"), new BigDecimal("5"), new BigDecimal("5"));

        assertThat(semPlano.comparable()).isFalse();
        assertThat(semPlano.quantityVariance()).isNull();
        // Zero seria dizer que a receita não pedia malte, e aí os 22 KG inteiros seriam desvio.
        assertThat(of(semPlano).consumptionVariance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("consumo do que a receita não pedia é desvio de consumo inteiro, e isso é zero de verdade")
    void consumoExtraEhZeroDeVerdade() {
        var extra = new MaterialVariance(UUID.randomUUID(), "Açúcar", "KG", BigDecimal.ZERO,
                new BigDecimal("3"), new BigDecimal("8"), new BigDecimal("8"));

        assertThat(extra.comparable()).isTrue();
        assertThat(extra.consumptionVariance()).isEqualByComparingTo("24");
        assertThat(extra.priceVariance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("render menos é desfavorável; perder menos não é")
    void oSinalDeVolumeDependeDoQueSeMede() {
        var rendeuMenos = new VolumeVariance(VolumeKind.YIELD, "transferido", new BigDecimal("400"),
                new BigDecimal("390"));
        var perdeuMenos = new VolumeVariance(VolumeKind.LOSS, "perda", new BigDecimal("10"),
                new BigDecimal("8"));

        assertThat(rendeuMenos.variance()).isEqualByComparingTo("-10");
        assertThat(rendeuMenos.variancePercent()).isEqualByComparingTo("-2.50");
        assertThat(rendeuMenos.unfavorable()).isTrue();
        assertThat(perdeuMenos.unfavorable()).isFalse();
    }

    @Test
    @DisplayName("perda sem esperado cadastrado é fato, não desvio")
    void perdaSemBaseNaoEhDesvio() {
        var perda = new VolumeVariance(VolumeKind.LOSS, "perda na transferência", null,
                new BigDecimal("8"));

        assertThat(perda.comparable()).isFalse();
        assertThat(perda.variance()).isNull();
        assertThat(perda.variancePercent()).isNull();
        // Sem base, chamar de desfavorável seria acusar a fábrica com um critério que ela não tem.
        assertThat(perda.unfavorable()).isFalse();
    }

    @Test
    @DisplayName("lacuna declarada deixa a variação incompleta")
    void lacunaDeixaIncompleta() {
        var variance = new BatchVariance(UUID.randomUUID(), "LOTE-100",
                List.of(material("Malte", "20", "20", "5", "5")), List.of(),
                List.of(new VarianceGap("envase", "este lote ainda não foi envasado")));

        assertThat(variance.incomplete()).isTrue();
        assertThat(variance.reconciles()).isTrue();
    }

    private static BatchVariance of(MaterialVariance... materials) {
        return new BatchVariance(UUID.randomUUID(), "LOTE-100", List.of(materials), List.of(), List.of());
    }

    private static MaterialVariance material(String name, String planned, String actual,
            String plannedCost, String actualCost) {
        return new MaterialVariance(UUID.randomUUID(), name, "KG", new BigDecimal(planned),
                new BigDecimal(actual), new BigDecimal(plannedCost), new BigDecimal(actualCost));
    }
}
