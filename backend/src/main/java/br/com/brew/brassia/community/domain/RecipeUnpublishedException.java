package br.com.brew.brassia.community.domain;

import java.util.UUID;

/**
 * Tentaram mexer numa publicação que já saiu de circulação (COM-001).
 *
 * <p>Recusar em vez de deixar passar: mudar licença ou visibilidade de algo despublicado daria a
 * impressão de que a mudança tem efeito, quando não há nada publicado para ela alcançar. Republicar é ato
 * novo, com data nova.
 */
public class RecipeUnpublishedException extends RuntimeException {

    public RecipeUnpublishedException(UUID id) {
        super("a publicação " + id + " não está mais no ar");
    }
}
