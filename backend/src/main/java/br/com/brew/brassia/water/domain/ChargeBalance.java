package br.com.brew.brassia.water.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Balanço de cargas de um perfil iônico (WTR-003): soma de cátions vs ânions em
 * mEq/L. Um perfil consistente tem diferença percentual dentro da tolerância;
 * fora disso é um alerta de inconsistência (nunca bloqueia).
 */
public record ChargeBalance(BigDecimal cationsMeqL, BigDecimal anionsMeqL, BigDecimal differenceMeqL,
        BigDecimal percentDifference, boolean withinTolerance) {

    /** Tolerância padrão da diferença percentual de cargas. */
    public static final BigDecimal TOLERANCE_PERCENT = new BigDecimal("5");

    // Massas molares (g/mol) e valências dos íons cervejeiros.
    private static final BigDecimal CA = new BigDecimal("40.08");
    private static final BigDecimal MG = new BigDecimal("24.31");
    private static final BigDecimal NA = new BigDecimal("22.99");
    private static final BigDecimal CL = new BigDecimal("35.45");
    private static final BigDecimal SO4 = new BigDecimal("96.06");
    private static final BigDecimal HCO3 = new BigDecimal("61.02");

    public static ChargeBalance of(IonProfile ions) {
        BigDecimal cations = meq(ions.calcium(), 2, CA)
                .add(meq(ions.magnesium(), 2, MG))
                .add(meq(ions.sodium(), 1, NA));
        BigDecimal anions = meq(ions.chloride(), 1, CL)
                .add(meq(ions.sulfate(), 2, SO4))
                .add(meq(ions.bicarbonate(), 1, HCO3));

        BigDecimal difference = cations.subtract(anions);
        BigDecimal total = cations.add(anions);
        BigDecimal percent = total.signum() == 0 ? BigDecimal.ZERO
                : difference.abs().multiply(new BigDecimal("200")).divide(total, 2, RoundingMode.HALF_UP);
        boolean within = percent.compareTo(TOLERANCE_PERCENT) <= 0;

        return new ChargeBalance(scale(cations), scale(anions), scale(difference), percent, within);
    }

    private static BigDecimal meq(BigDecimal mgPerL, int valence, BigDecimal molarMass) {
        return mgPerL.multiply(BigDecimal.valueOf(valence)).divide(molarMass, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
