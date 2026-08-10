package br.com.brew.brassia.fieldfeedback.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A amostra física da reclamação (FLD-001).
 *
 * <p><strong>Sem amostra, quase nenhuma reclamação se confirma.</strong> Um off-flavor relatado por
 * telefone não se mede; o mesmo off-flavor numa garrafa em mãos, sim. Por isso o estado da amostra é
 * campo do registro e não observação solta: uma investigação que começa sem saber se há o que analisar
 * começa perdendo tempo.
 */
public record SampleRetention(Status status, String location) {

    public enum Status {
        /** A amostra está com a cervejaria. */
        RETAINED,
        /** O consumidor tem a amostra — dá para pedir. */
        WITH_CONSUMER,
        /** Descartada ou consumida: não há o que analisar. */
        UNAVAILABLE,
        /** Ninguém perguntou. Diferente de não haver. */
        UNKNOWN
    }

    public SampleRetention {
        Objects.requireNonNull(status, "status");
        // Amostra retida sem lugar declarado é amostra que ninguém acha quando precisa.
        if (status == Status.RETAINED && (location == null || location.isBlank())) {
            throw new IllegalArgumentException("amostra retida precisa de local declarado");
        }
    }

    public static SampleRetention unknown() {
        return new SampleRetention(Status.UNKNOWN, null);
    }

    public boolean analyzable() {
        return status == Status.RETAINED;
    }

    public Optional<String> where() {
        return Optional.ofNullable(location);
    }
}
