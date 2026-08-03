package br.com.brew.brassia.quality.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Limites de especificação de um parâmetro.
 *
 * <p><strong>Limite unilateral é caso normal, não exceção:</strong> muita especificação real é só
 * teto ("O₂ ≤ 50 ppb") ou só piso ("atenuação ≥ 75%"). Exigir os dois obrigaria quem escreve o
 * plano a inventar o limite que falta, e um limite inventado vira desvio inventado.
 *
 * <p>A faixa é <strong>inclusiva</strong>: o valor exatamente no limite está conforme. Um limite
 * é o último valor aceitável, não o primeiro recusado.
 */
public record SpecLimits(BigDecimal min, BigDecimal max, BigDecimal target, String unit) {

    public SpecLimits {
        unit = requireUnit(unit);
        if (min == null && max == null) {
            throw new IllegalArgumentException("informe ao menos um limite: mínimo ou máximo");
        }
        if (min != null && max != null && min.compareTo(max) >= 0) {
            throw new IllegalArgumentException("o mínimo deve ser menor que o máximo");
        }
        if (target != null) {
            if (min != null && target.compareTo(min) < 0) {
                throw new IllegalArgumentException("o alvo não pode ser menor que o mínimo");
            }
            if (max != null && target.compareTo(max) > 0) {
                throw new IllegalArgumentException("o alvo não pode ser maior que o máximo");
            }
        }
    }

    /** A violação, quando houver: qual lado da faixa foi rompido e por qual limite. */
    public Optional<Violation> violation(BigDecimal value) {
        Objects.requireNonNull(value, "valor medido");
        if (min != null && value.compareTo(min) < 0) {
            return Optional.of(new Violation(Bound.BELOW_MIN, min));
        }
        if (max != null && value.compareTo(max) > 0) {
            return Optional.of(new Violation(Bound.ABOVE_MAX, max));
        }
        return Optional.empty();
    }

    public boolean conforms(BigDecimal value) {
        return violation(value).isEmpty();
    }

    public String describe() {
        if (min != null && max != null) {
            return "%s a %s %s".formatted(plain(min), plain(max), unit);
        }
        return min != null ? "≥ %s %s".formatted(plain(min), unit) : "≤ %s %s".formatted(plain(max), unit);
    }

    /**
     * O limite vem do banco com a escala da coluna (NUMERIC(14,4)), então "50" volta como
     * "50.0000". Na tela isso sugere uma precisão que o limite não tem — quem escreveu "≤ 50 ppb"
     * não quis dizer "≤ 50,0000 ppb".
     */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public enum Bound {
        BELOW_MIN,
        ABOVE_MAX
    }

    public record Violation(Bound bound, BigDecimal limit) {}

    private static String requireUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        var trimmed = unit.trim();
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("unidade excede 20 caracteres");
        }
        return trimmed;
    }
}
