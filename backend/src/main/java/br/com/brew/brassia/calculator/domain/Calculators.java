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
                new CalculatorSpec("ibu-tinseth", "IBU (Tinseth)", List.of("alphaAcid", "weightGrams", "timeMinutes",
                        "volumeLiters", "boilGravity"), "IBU", "Amargor de uma adição de lúpulo (Tinseth)."));
    }

    public CalculationResult compute(String id, Map<String, BigDecimal> inputs) {
        return switch (id == null ? "" : id) {
            case "abv" -> abv(inputs);
            case "apparent-attenuation" -> attenuation(inputs);
            case "sg-to-plato" -> sgToPlato(inputs);
            case "srm-to-ebc" -> srmToEbc(inputs);
            case "celsius-to-fahrenheit" -> celsiusToFahrenheit(inputs);
            case "dilution-water" -> dilution(inputs);
            case "ibu-tinseth" -> ibuTinseth(inputs);
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
