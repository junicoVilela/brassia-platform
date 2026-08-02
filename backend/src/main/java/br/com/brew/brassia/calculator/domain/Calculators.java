package br.com.brew.brassia.calculator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hub de calculadoras cervejeiras determinísticas (CAL-001). Fórmulas ficam aqui,
 * no domínio; frontend e receita reutilizam este mesmo serviço, sem replicar.
 */
public final class Calculators {

    private static final String VERSION = "1.0";

    public List<CalculatorSpec> catalog() {
        return List.of(
                new CalculatorSpec("abv", "Teor alcoólico (ABV)", List.of("og", "fg"), "%",
                        "ABV a partir de OG e FG."),
                new CalculatorSpec("apparent-attenuation", "Atenuação aparente", List.of("og", "fg"), "%",
                        "Atenuação aparente a partir de OG e FG."),
                new CalculatorSpec("sg-to-plato", "SG → Plato", List.of("sg"), "°P",
                        "Converte densidade (SG) em graus Plato."),
                new CalculatorSpec("srm-to-ebc", "SRM → EBC", List.of("srm"), "EBC", "Converte cor SRM em EBC."),
                new CalculatorSpec("celsius-to-fahrenheit", "°C → °F", List.of("celsius"), "°F",
                        "Converte temperatura."),
                new CalculatorSpec("dilution-water", "Diluição de OG", List.of("currentOg", "currentVolume",
                        "targetOg"), "L", "Água a adicionar para atingir a OG alvo."),
                new CalculatorSpec("concentration-boiloff", "Concentração por evaporação",
                        List.of("currentOg", "currentVolume", "targetOg"), "L",
                        "Volume a evaporar para elevar a OG à alvo."),
                new CalculatorSpec("hydrometer-temp-correction", "Correção de densidade por temperatura",
                        List.of("measuredSg", "sampleTempC", "calibrationTempC"), "SG",
                        "Corrige a leitura do densímetro pela temperatura da amostra."),
                new CalculatorSpec("volume-topup", "Ajuste de volume (completar)",
                        List.of("currentVolume", "targetVolume"), "L",
                        "Água a adicionar para atingir o volume alvo."),
                new CalculatorSpec("ibu-tinseth", "IBU (Tinseth)", List.of("alphaAcid", "weightGrams", "timeMinutes",
                        "volumeLiters", "boilGravity"), "IBU", "Amargor de uma adição de lúpulo (Tinseth)."),
                new CalculatorSpec("co2-residual", "CO₂ residual", List.of("tempC"), "vol",
                        "CO₂ ainda dissolvido na cerveja pela temperatura mais alta que ela atingiu."),
                new CalculatorSpec("priming-sugar", "Açúcar de priming",
                        List.of("targetVolumes", "residualVolumes", "beerVolumeLiters", "sugarYield"), "g",
                        "Massa de açúcar para completar do CO₂ residual até os volumes alvo."),
                new CalculatorSpec("forced-carbonation-pressure", "Pressão de carbonatação forçada",
                        List.of("targetVolumes", "tempC"), "bar",
                        "Pressão de equilíbrio para atingir os volumes alvo na temperatura informada."));
    }

    public CalculationResult compute(String id, Map<String, BigDecimal> inputs) {
        return switch (id == null ? "" : id) {
            case "abv" -> abv(inputs);
            case "apparent-attenuation" -> attenuation(inputs);
            case "sg-to-plato" -> sgToPlato(inputs);
            case "srm-to-ebc" -> srmToEbc(inputs);
            case "celsius-to-fahrenheit" -> celsiusToFahrenheit(inputs);
            case "dilution-water" -> dilution(inputs);
            case "concentration-boiloff" -> concentration(inputs);
            case "hydrometer-temp-correction" -> hydrometerTempCorrection(inputs);
            case "volume-topup" -> volumeTopUp(inputs);
            case "ibu-tinseth" -> ibuTinseth(inputs);
            case "co2-residual" -> co2Residual(inputs);
            case "priming-sugar" -> primingSugar(inputs);
            case "forced-carbonation-pressure" -> forcedCarbonationPressure(inputs);
            default -> throw new IllegalArgumentException("calculadora desconhecida: " + id);
        };
    }

    private CalculationResult abv(Map<String, BigDecimal> in) {
        var og = require(in, "og");
        var fg = require(in, "fg");
        var value = og.subtract(fg).multiply(new BigDecimal("131.25")).setScale(2, RoundingMode.HALF_UP);
        var alerts = new ArrayList<String>();
        if (fg.compareTo(og) > 0) {
            alerts.add("FG maior que OG: resultado negativo (dados inconsistentes).");
        }
        return result("abv", in, value, "%", "ABV = (OG − FG) × 131.25", List.of("OG/FG em SG"), alerts);
    }

    private CalculationResult attenuation(Map<String, BigDecimal> in) {
        var og = require(in, "og");
        var fg = require(in, "fg");
        BigDecimal ogPoints = points(og);
        BigDecimal fgPoints = points(fg);
        var alerts = new ArrayList<String>();
        BigDecimal value;
        if (ogPoints.signum() <= 0) {
            value = BigDecimal.ZERO;
            alerts.add("OG deve ser maior que 1.000.");
        } else {
            value = ogPoints.subtract(fgPoints).multiply(HUNDRED).divide(ogPoints, 2, RoundingMode.HALF_UP);
        }
        return result("apparent-attenuation", in, value, "%",
                "Att = (OGpts − FGpts) / OGpts × 100", List.of("OG/FG em SG"), alerts);
    }

    private CalculationResult sgToPlato(Map<String, BigDecimal> in) {
        double sg = require(in, "sg").doubleValue();
        double plato = -616.868 + 1111.14 * sg - 630.272 * sg * sg + 135.997 * sg * sg * sg;
        return result("sg-to-plato", in, round(plato, 2), "°P",
                "Plato = −616.868 + 1111.14·SG − 630.272·SG² + 135.997·SG³", List.of("polinômio ASBC"), List.of());
    }

    private CalculationResult srmToEbc(Map<String, BigDecimal> in) {
        var srm = require(in, "srm");
        return result("srm-to-ebc", in, srm.multiply(new BigDecimal("1.97")).setScale(2, RoundingMode.HALF_UP),
                "EBC", "EBC = SRM × 1.97", List.of(), List.of());
    }

    private CalculationResult celsiusToFahrenheit(Map<String, BigDecimal> in) {
        var c = require(in, "celsius");
        var value = c.multiply(new BigDecimal("9")).divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP)
                .add(new BigDecimal("32")).setScale(2, RoundingMode.HALF_UP);
        return result("celsius-to-fahrenheit", in, value, "°F", "°F = °C × 9/5 + 32", List.of(), List.of());
    }

    private CalculationResult dilution(Map<String, BigDecimal> in) {
        var currentOg = require(in, "currentOg");
        var currentVolume = require(in, "currentVolume");
        var targetOg = require(in, "targetOg");
        BigDecimal currentPoints = points(currentOg);
        BigDecimal targetPoints = points(targetOg);
        var alerts = new ArrayList<String>();
        BigDecimal water;
        if (targetPoints.signum() <= 0 || targetPoints.compareTo(currentPoints) >= 0) {
            water = BigDecimal.ZERO;
            alerts.add("OG alvo deve ser menor que a atual (diluição só reduz a gravidade).");
        } else {
            water = currentVolume.multiply(currentPoints.divide(targetPoints, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE)).setScale(3, RoundingMode.HALF_UP);
        }
        return result("dilution-water", in, water, "L",
                "Água = V × (OGpts_atual / OGpts_alvo − 1)", List.of("mistura ideal, sem contração"), alerts);
    }

    private CalculationResult concentration(Map<String, BigDecimal> in) {
        var currentOg = require(in, "currentOg");
        var currentVolume = require(in, "currentVolume");
        var targetOg = require(in, "targetOg");
        BigDecimal currentPoints = points(currentOg);
        BigDecimal targetPoints = points(targetOg);
        var alerts = new ArrayList<String>();
        BigDecimal evaporate;
        if (currentPoints.signum() <= 0 || targetPoints.compareTo(currentPoints) <= 0) {
            evaporate = BigDecimal.ZERO;
            alerts.add("OG alvo deve ser maior que a atual (concentração só aumenta a gravidade).");
        } else {
            var finalVolume = currentVolume.multiply(currentPoints.divide(targetPoints, 6, RoundingMode.HALF_UP));
            evaporate = currentVolume.subtract(finalVolume).setScale(3, RoundingMode.HALF_UP);
        }
        return result("concentration-boiloff", in, evaporate, "L",
                "Evaporar = V × (1 − OGpts_atual / OGpts_alvo)", List.of("extrato conservado, sem perdas"), alerts);
    }

    private CalculationResult hydrometerTempCorrection(Map<String, BigDecimal> in) {
        double measured = require(in, "measuredSg").doubleValue();
        double sampleF = require(in, "sampleTempC").doubleValue() * 9.0 / 5.0 + 32.0;
        double calibF = require(in, "calibrationTempC").doubleValue() * 9.0 / 5.0 + 32.0;
        double corrected = measured * densityFactor(sampleF) / densityFactor(calibF);
        return result("hydrometer-temp-correction", in, round(corrected, 4), "SG",
                "SG_corr = SG × f(T_amostra) / f(T_calib), f polinomial (°F)",
                List.of("fórmula padrão de correção por temperatura"), List.of());
    }

    private static double densityFactor(double tempF) {
        return 1.00130346 - 1.34722124e-4 * tempF + 2.04052596e-6 * tempF * tempF
                - 2.32820948e-9 * tempF * tempF * tempF;
    }

    private CalculationResult volumeTopUp(Map<String, BigDecimal> in) {
        var currentVolume = require(in, "currentVolume");
        var targetVolume = require(in, "targetVolume");
        var alerts = new ArrayList<String>();
        BigDecimal water;
        if (targetVolume.compareTo(currentVolume) <= 0) {
            water = BigDecimal.ZERO;
            alerts.add("volume alvo deve ser maior que o atual (completar só adiciona).");
        } else {
            water = targetVolume.subtract(currentVolume).setScale(3, RoundingMode.HALF_UP);
        }
        return result("volume-topup", in, water, "L", "Água = V_alvo − V_atual", List.of(), alerts);
    }

    private CalculationResult ibuTinseth(Map<String, BigDecimal> in) {
        double alpha = require(in, "alphaAcid").doubleValue() / 100.0;
        double weight = require(in, "weightGrams").doubleValue();
        double time = require(in, "timeMinutes").doubleValue();
        double volume = require(in, "volumeLiters").doubleValue();
        double gravity = require(in, "boilGravity").doubleValue();
        var alerts = new ArrayList<String>();
        if (volume <= 0) {
            return result("ibu-tinseth", in, BigDecimal.ZERO, "IBU", "Tinseth",
                    List.of(), List.of("volume deve ser positivo"));
        }
        double mgAlpha = alpha * weight * 1000.0 / volume;
        double bigness = 1.65 * Math.pow(0.000125, gravity - 1.0);
        double timeFactor = (1 - Math.exp(-0.04 * time)) / 4.15;
        double ibu = mgAlpha * bigness * timeFactor;
        return result("ibu-tinseth", in, round(ibu, 1), "IBU",
                "Tinseth: mgAA × 1.65·0.000125^(G−1) × (1−e^(−0.04·t))/4.15",
                List.of("uma adição", "densidade de fervura em SG"), alerts);
    }

    /** Densidade do CO₂ a 0 °C e 1 atm: 1 "volume" equivale a 1,96 g de CO₂ por litro de cerveja. */
    private static final double CO2_GRAMS_PER_VOLUME_LITER = 1.96;

    private static final double PSI_TO_BAR = 0.0689476;

    /**
     * CO₂ ainda dissolvido na cerveja (PKG-002). Depende da <strong>maior</strong> temperatura que
     * a cerveja atingiu depois da fermentação primária: é ela que definiu quanto CO₂ escapou.
     * Ignorar esse residual superestima o priming e leva a sobrepressão na embalagem.
     */
    private CalculationResult co2Residual(Map<String, BigDecimal> in) {
        double tempF = require(in, "tempC").doubleValue() * 9.0 / 5.0 + 32.0;
        double volumes = 3.0378 - 0.050062 * tempF + 0.00026555 * tempF * tempF;
        var alerts = new ArrayList<String>();
        if (volumes < 0) {
            volumes = 0;
            alerts.add("Temperatura fora da faixa da fórmula; residual tratado como zero.");
        }
        return result("co2-residual", in, round(volumes, 3), "vol",
                "vol = 3.0378 − 0.050062·T + 0.00026555·T² (T em °F)",
                List.of("temperatura é a mais alta atingida após a fermentação primária"), alerts);
    }

    /**
     * Açúcar de priming (PKG-002). Só o que falta entre o residual e o alvo é açucarado, e o
     * rendimento em CO₂ é entrada explícita — cada açúcar rende diferente, e escondê-lo num número
     * fixo esconderia a hipótese do cálculo.
     */
    private CalculationResult primingSugar(Map<String, BigDecimal> in) {
        double target = require(in, "targetVolumes").doubleValue();
        double residual = require(in, "residualVolumes").doubleValue();
        double volume = require(in, "beerVolumeLiters").doubleValue();
        double yield = require(in, "sugarYield").doubleValue();
        var alerts = new ArrayList<String>();
        if (yield <= 0) {
            return result("priming-sugar", in, BigDecimal.ZERO, "g", "priming", List.of(),
                    List.of("rendimento do açúcar deve ser positivo"));
        }
        if (volume <= 0) {
            return result("priming-sugar", in, BigDecimal.ZERO, "g", "priming", List.of(),
                    List.of("volume de cerveja deve ser positivo"));
        }
        double missing = target - residual;
        if (missing <= 0) {
            alerts.add("A cerveja já tem o CO₂ alvo dissolvido; priming adicional causaria sobrepressão.");
            missing = 0;
        }
        double grams = missing * volume * CO2_GRAMS_PER_VOLUME_LITER / yield;
        return result("priming-sugar", in, round(grams, 1), "g",
                "g = (vol_alvo − vol_residual) × V × 1,96 / rendimento",
                List.of("1 volume = 1,96 g de CO₂ por litro", "fermentação completa do açúcar adicionado"),
                alerts);
    }

    /**
     * Pressão de equilíbrio para carbonatação forçada (PKG-002). Temperatura é a variável dominante:
     * a mesma pressão carbonata muito menos numa cerveja quente do que numa fria.
     */
    private CalculationResult forcedCarbonationPressure(Map<String, BigDecimal> in) {
        double target = require(in, "targetVolumes").doubleValue();
        double tempF = require(in, "tempC").doubleValue() * 9.0 / 5.0 + 32.0;
        double henry = 0.01821 + 0.09011 * Math.exp(-(tempF - 32.0) / 43.11);
        double psi = (target + 0.003342) / henry - 14.695;
        var alerts = new ArrayList<String>();
        if (psi < 0) {
            alerts.add("Nessa temperatura a cerveja já passa dos volumes alvo sem pressão aplicada.");
            psi = 0;
        }
        return result("forced-carbonation-pressure", in, round(psi * PSI_TO_BAR, 2), "bar",
                "P(psig) = (vol + 0.003342) / (0.01821 + 0.09011·e^(−(T−32)/43.11)) − 14.695 (T em °F)",
                List.of("equilíbrio atingido", "pressão manométrica convertida a bar"), alerts);
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal points(BigDecimal sg) {
        return sg.subtract(BigDecimal.ONE).multiply(new BigDecimal("1000"));
    }

    private static BigDecimal require(Map<String, BigDecimal> inputs, String key) {
        BigDecimal value = inputs == null ? null : inputs.get(key);
        if (value == null) {
            throw new IllegalArgumentException("entrada obrigatória ausente: " + key);
        }
        return value;
    }

    private static BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static CalculationResult result(String calc, Map<String, BigDecimal> inputs, BigDecimal value,
            String unit, String method, List<String> assumptions, List<String> alerts) {
        return new CalculationResult(calc, inputs, value, unit, method, VERSION, assumptions, alerts);
    }
}
