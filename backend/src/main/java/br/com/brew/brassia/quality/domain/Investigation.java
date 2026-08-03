package br.com.brew.brassia.quality.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Causa raiz e como se chegou a ela.
 *
 * <p>O método é obrigatório junto da causa: "contaminação" sem dizer como isso foi determinado é
 * palpite com aparência de conclusão, e é sobre essa conclusão que a ação preventiva será desenhada.
 */
public record Investigation(String rootCause, String method, Instant investigatedAt, UUID investigatedBy) {

    public Investigation {
        rootCause = Texts.require(rootCause, "causa raiz", 1000);
        method = Texts.require(method, "método de investigação", 200);
        Objects.requireNonNull(investigatedAt, "instante da investigação");
        Objects.requireNonNull(investigatedBy, "responsável pela investigação");
    }
}
