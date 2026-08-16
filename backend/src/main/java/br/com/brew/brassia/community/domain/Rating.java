package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A nota que uma pessoa deu a uma publicação (COM-005).
 *
 * <p><strong>Uma nota por pessoa, e ela se troca em vez de acumular.</strong> Deixar a mesma pessoa
 * avaliar duas vezes transformaria a média numa contagem de quem insistiu mais — e é o jeito mais simples
 * de manipular reputação sem precisar de robô nenhum.
 *
 * <p><strong>Não se avalia a própria receita.</strong> Não é desconfiança: é que a nota do autor não
 * informa ninguém, e uma média que inclui o autor mede outra coisa. A regra vive no caso de uso, que é
 * quem conhece o dono da publicação.
 */
public record Rating(UUID publicationId, UUID userId, int value, Instant ratedAt) {

    private static final int MIN = 1;
    private static final int MAX = 5;

    public Rating {
        Objects.requireNonNull(publicationId, "publicação");
        Objects.requireNonNull(userId, "quem avaliou");
        Objects.requireNonNull(ratedAt, "quando avaliou");
        if (value < MIN || value > MAX) {
            // Escala fechada de 1 a 5. Zero seria "não avaliou", que é a ausência de linha — e permitir
            // zero faria "sem opinião" e "péssima" virarem o mesmo número na média.
            throw new IllegalArgumentException("a nota vai de " + MIN + " a " + MAX);
        }
    }
}
