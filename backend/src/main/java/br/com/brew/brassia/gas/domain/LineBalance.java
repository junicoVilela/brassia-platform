package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Recomendação de balanceamento de linha (GAS-002).
 *
 * <p>É <strong>recomendação</strong>, não comando: nenhuma válvula ou regulador é ajustado pelo
 * sistema. O resultado vem com o método, os limites que ele respeita e os avisos de segurança que o
 * cervejeiro precisa ler antes de montar a linha.
 *
 * <p>A pressão aplicada não é escolha livre: ela é ditada pelo equilíbrio de carbonatação na
 * temperatura de serviço. Servir a uma pressão diferente faz o barril ganhar ou perder CO₂ ao longo
 * do tempo, e a cerveja sai do padrão sem que ninguém tenha mexido nela.
 */
public record LineBalance(BigDecimal appliedPressureBar, BigDecimal recommendedLengthMeters,
        BigDecimal hydrostaticBar, BigDecimal effectiveResistanceBarPerMeter, BigDecimal targetFlowLpm,
        BigDecimal servingTempC, BigDecimal targetCo2Volumes, String material, BigDecimal internalDiameterMm,
        String calculationMethod, String calculatorVersion, boolean feasible, List<Warning> warnings) {

    /** Aviso com código estável e frase segura; {@code safety} marca o que envolve risco físico. */
    public record Warning(String code, String message, boolean safety) {

        public Warning {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Lembrete permanente: o sistema calcula, quem ajusta a rede é a pessoa. */
    public static final Warning MANUAL_ADJUSTMENT_ONLY = new Warning("manual_adjustment_only",
            "O sistema não ajusta válvula nem regulador: aplique a pressão e o comprimento à mão e "
                    + "confira a linha antes de servir.", true);

    public LineBalance {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /** Avisos de segurança; sempre há ao menos o lembrete de ajuste manual. */
    public List<Warning> safetyWarnings() {
        return warnings.stream().filter(Warning::safety).toList();
    }

    /**
     * Monta a recomendação a partir do comprimento já calculado pelo hub de calculadoras.
     *
     * @param networkMaxPressureBar teto da rede de gás que serve o ponto, quando existe conexão
     *                              montada; nulo quando o ponto ainda não tem linha de gás
     */
    public static LineBalance of(BigDecimal appliedPressureBar, BigDecimal lengthMeters,
            BigDecimal hydrostaticBar, BigDecimal effectiveResistance, BigDecimal targetFlowLpm,
            BigDecimal servingTempC, BigDecimal targetCo2Volumes, LineResistance tubing,
            BigDecimal networkMaxPressureBar, String calculationMethod, String calculatorVersion,
            List<String> calculatorAlerts) {
        var warnings = new ArrayList<Warning>();
        warnings.add(MANUAL_ADJUSTMENT_ONLY);

        var feasible = lengthMeters.signum() > 0;
        if (!feasible) {
            warnings.add(new Warning("no_balance_possible",
                    "Nessa montagem a pressão aplicada não vence o desnível mais a pressão residual: "
                            + "a cerveja não flui.", true));
        }
        // O teto da rede é limite físico dos componentes: passar dele é risco, não ajuste fino.
        if (networkMaxPressureBar != null && appliedPressureBar.compareTo(networkMaxPressureBar) > 0) {
            warnings.add(new Warning("above_network_limit",
                    "A pressão de serviço (" + plain(appliedPressureBar) + " bar) passa do limite da rede de "
                            + "gás que serve este ponto (" + plain(networkMaxPressureBar) + " bar).", true));
        }
        for (var alert : calculatorAlerts) {
            warnings.add(new Warning("calculation_alert", alert, false));
        }

        return new LineBalance(appliedPressureBar, lengthMeters, hydrostaticBar, effectiveResistance,
                targetFlowLpm, servingTempC, targetCo2Volumes, tubing.material(), tubing.internalDiameterMm(),
                calculationMethod, calculatorVersion, feasible, List.copyOf(warnings));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
