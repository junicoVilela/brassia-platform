package br.com.brew.brassia.sensory.domain;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Set;

/**
 * Código cego da amostra: três dígitos aleatórios, como manda a prática sensorial.
 *
 * <p><strong>Aleatório, não sequencial.</strong> Código em sequência vazaria a ordem de preparo, e
 * ordem é informação: o provador que percebe "a 001 é a primeira" começa a inferir o que está
 * provando. Também evitamos o 000 e códigos com significado óbvio pelo mesmo motivo.
 */
public record BlindCode(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN = 100;
    private static final int MAX = 999;

    public BlindCode {
        Objects.requireNonNull(value, "código cego");
        if (!value.matches("\\d{3}")) {
            throw new IllegalArgumentException("o código cego tem exatamente três dígitos");
        }
        if (value.equals("000")) {
            throw new IllegalArgumentException("000 não é código cego válido");
        }
    }

    /**
     * Sorteia um código inédito na sessão. Recebe os já usados porque a unicidade é da sessão —
     * duas amostras com o mesmo código tornariam as fichas indistinguíveis.
     */
    public static BlindCode randomExcluding(Set<String> used) {
        Objects.requireNonNull(used, "códigos já usados");
        if (used.size() >= MAX - MIN + 1) {
            throw new IllegalStateException("não há código cego disponível para a sessão");
        }
        String candidate;
        do {
            candidate = String.valueOf(RANDOM.nextInt(MIN, MAX + 1));
        } while (used.contains(candidate));
        return new BlindCode(candidate);
    }
}
