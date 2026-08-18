package br.com.brew.brassia.forecast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionCapacityTest {

    private static ProductionCapacity capacidade(int dias, ProductionCapacity.Tank... tanques) {
        return new ProductionCapacity(List.of(tanques), dias);
    }

    private static ProductionCapacity.Tank tanque(String volume, int ciclo) {
        return new ProductionCapacity.Tank("FV-01", new BigDecimal(volume), ciclo);
    }

    @Test
    void semTanqueDeclaradoARespostaENaoSeiENaoZero() {
        // Zero diria que a cervejaria não consegue produzir nada, e alguém planejaria em cima disso. É a
        // mesma escolha que a previsão de demanda faz com histórico curto.
        var c = capacidade(30);

        assertThat(c.known()).isFalse();
        assertThat(c.fits(new BigDecimal("1000"))).isEmpty();
        assertThat(c.headroomLiters(new BigDecimal("1000"))).isEmpty();
        assertThat(c.utilizationPercent(new BigDecimal("1000"))).isEmpty();
    }

    @Test
    void oLoteQueNaoTerminaNoPeriodoNaoConta() {
        // Contá-lo pela fração faria a capacidade incluir cerveja que ainda estará fermentando quando o
        // mês virar.
        var c = capacidade(30, tanque("1000", 14));

        // 30 ÷ 14 = 2 ciclos inteiros, e não 2,14.
        assertThat(c.litersInPeriod()).isEqualByComparingTo("2000");
    }

    @Test
    void aCapacidadeSomaOsTanquesDeclarados() {
        var c = capacidade(30, tanque("1000", 14),
                new ProductionCapacity.Tank("FV-02", new BigDecimal("500"), 10));

        // 1000×2 + 500×3
        assertThat(c.litersInPeriod()).isEqualByComparingTo("3500");
        assertThat(c.known()).isTrue();
    }

    @Test
    void aDemandaQueNaoCabeApareceComoFaltaENaoComoZero() {
        // A falta é a informação que importa: "sobra -800 L" diz quanto precisa mudar; "não cabe" não.
        var c = capacidade(30, tanque("1000", 14));

        assertThat(c.fits(new BigDecimal("2800"))).contains(false);
        assertThat(c.headroomLiters(new BigDecimal("2800"))).contains(new BigDecimal("-800"));
    }

    @Test
    void aDemandaQueCabeDizQuantoOcupa() {
        var c = capacidade(30, tanque("1000", 14));

        assertThat(c.fits(new BigDecimal("1500"))).contains(true);
        assertThat(c.utilizationPercent(new BigDecimal("1500"))).contains(new BigDecimal("75.0"));
    }

    @Test
    void cicloZeroSeriaProducaoInfinita() {
        // O erro só apareceria como uma capacidade absurda que ninguém questiona porque veio do sistema.
        assertThatThrownBy(() -> tanque("1000", 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos um dia");
        assertThatThrownBy(() -> tanque("0", 14)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void oPeriodoPrecisaDePeloMenosUmDia() {
        assertThatThrownBy(() -> capacidade(0, tanque("1000", 14)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
