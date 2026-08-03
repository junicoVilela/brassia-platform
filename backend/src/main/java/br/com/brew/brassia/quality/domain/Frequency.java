package br.com.brew.brassia.quality.domain;

import java.util.Objects;

/**
 * Cadência da medição: o tipo e, quando ele pede, o valor.
 *
 * @param everyHours obrigatório em {@code PER_HOURS} e proibido no resto — "a cada lote, de 4 em
 *                   4 horas" não descreve cadência nenhuma
 */
public record Frequency(FrequencyKind kind, Integer everyHours) {

    public Frequency {
        Objects.requireNonNull(kind, "tipo de frequência é obrigatório");
        if (kind.needsValue()) {
            if (everyHours == null || everyHours <= 0) {
                throw new IllegalArgumentException("frequência por horas exige um intervalo positivo");
            }
        } else if (everyHours != null) {
            throw new IllegalArgumentException("só a frequência por horas aceita intervalo");
        }
    }

    public static Frequency perBatch() {
        return new Frequency(FrequencyKind.PER_BATCH, null);
    }

    public String describe() {
        return kind == FrequencyKind.PER_HOURS ? "A cada %d horas".formatted(everyHours) : kind.label();
    }
}
