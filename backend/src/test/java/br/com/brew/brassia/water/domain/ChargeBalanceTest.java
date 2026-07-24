package br.com.brew.brassia.water.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ChargeBalanceTest {

    private static IonProfile ions(String ca, String mg, String na, String so4, String cl, String hco3) {
        return new IonProfile(new BigDecimal(ca), new BigDecimal(mg), new BigDecimal(na), new BigDecimal(so4),
                new BigDecimal(cl), new BigDecimal(hco3));
    }

    @Test
    void balancedProfileIsWithinTolerance() {
        // Perfil realista aproximadamente balanceado.
        var balance = ChargeBalance.of(ions("50", "10", "20", "60", "40", "100"));

        assertThat(balance.cationsMeqL()).isPositive();
        assertThat(balance.anionsMeqL()).isPositive();
        assertThat(balance.withinTolerance()).isTrue();
        assertThat(balance.percentDifference()).isLessThanOrEqualTo(ChargeBalance.TOLERANCE_PERCENT);
    }

    @Test
    void cationHeavyProfileRaisesAlert() {
        // Só cátions: forte desbalanço → fora da tolerância.
        var balance = ChargeBalance.of(ions("200", "0", "0", "0", "0", "0"));

        assertThat(balance.withinTolerance()).isFalse();
        assertThat(balance.differenceMeqL()).isPositive();
        assertThat(balance.percentDifference()).isGreaterThan(ChargeBalance.TOLERANCE_PERCENT);
    }
}
