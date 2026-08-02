package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackagingRunTest {

    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-20T15:00:00Z");

    private static PackagingRun run(String inputLiters, int produced, int rejected) {
        return PackagingRun.execute(PLAN, BREWERY, BATCH, new BigDecimal("355"), new BigDecimal(inputLiters),
                produced, rejected, null, AT, ACTOR);
    }

    @Test
    void derivesVolumesAndClosesTheBalance() {
        // 780 boas + 12 rejeitadas de 355 ml = 276,9 + 4,26 = 281,16 L; entraram 284 L.
        var run = run("284", 780, 12);

        assertThat(run.packagedVolumeLiters()).isEqualByComparingTo("276.900");
        assertThat(run.rejectedVolumeLiters()).isEqualByComparingTo("4.260");
        assertThat(run.lossesLiters()).isEqualByComparingTo("2.840");
        // Tudo o que saiu do tanque está em alguma coluna: o balanço fecha por construção.
        assertThat(run.packagedVolumeLiters().add(run.rejectedVolumeLiters()).add(run.lossesLiters()))
                .isEqualByComparingTo(run.inputVolumeLiters());
    }

    @Test
    void refusesUnitsThatHoldMoreBeerThanLeftTheTank() {
        // 800 unidades de 355 ml = 284 L, mas só saíram 280 L do tanque.
        assertThatThrownBy(() -> run("280", 800, 0))
                .isInstanceOf(VolumeBalanceException.class)
                .satisfies(e -> {
                    var ex = (VolumeBalanceException) e;
                    assertThat(ex.inputVolumeLiters()).isEqualByComparingTo("280");
                    assertThat(ex.packagedVolumeLiters()).isEqualByComparingTo("284.000");
                    assertThat(ex.shortfallLiters()).isEqualByComparingTo("4.000");
                });
    }

    @Test
    void rejectedUnitsCountAgainstTheBalanceToo() {
        // 780 boas cabem em 280 L, mas 780 + 30 rejeitadas não.
        assertThat(run("280", 780, 0).lossesLiters()).isEqualByComparingTo("3.100");
        assertThatThrownBy(() -> run("280", 780, 30)).isInstanceOf(VolumeBalanceException.class);
    }

    @Test
    void rejectedUnitsConsumePackagingLikeGoodOnes() {
        // Uma lata cheia e descartada é uma lata gasta.
        assertThat(run("284", 780, 12).containersConsumed()).isEqualTo(792);
        assertThat(run("284", 792, 0).containersConsumed()).isEqualTo(792);
    }

    @Test
    void reportsLossAsPercentOfWhatLeftTheTank() {
        assertThat(run("284", 780, 12).lossPercent()).isEqualByComparingTo("1.00");
        assertThat(run("284", 800, 0).lossPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void exactBalanceLeavesNoLoss() {
        var run = run("284", 800, 0);

        assertThat(run.lossesLiters()).isEqualByComparingTo("0.000");
        assertThat(run.packagedVolumeLiters()).isEqualByComparingTo("284.000");
    }

    @Test
    void refusesRunWithoutAnyUnitOrWithoutInput() {
        assertThatThrownBy(() -> run("284", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nenhuma unidade");
        assertThatThrownBy(() -> run("0", 800, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run("284", -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run("284", 800, -5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsRunMadeOnlyOfRejects() {
        // Linha desregulada: tudo rejeitado ainda é execução, e consumiu embalagem.
        var run = run("284", 0, 100);

        assertThat(run.producedUnits()).isZero();
        assertThat(run.containersConsumed()).isEqualTo(100);
        assertThat(run.packagedVolumeLiters()).isEqualByComparingTo("0.000");
    }
}
