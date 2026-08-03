package br.com.brew.brassia.quality.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Verificação de eficácia: a ação funcionou?
 *
 * <p>A evidência é obrigatória nos dois resultados. "Funcionou" sem dizer como se sabe disso é a
 * mesma coisa que não ter verificado, e é justamente esse registro que uma auditoria vai pedir.
 */
public record Verification(boolean effective, String evidence, Instant verifiedAt, UUID verifiedBy) {

    public Verification {
        evidence = Texts.require(evidence, "evidência da verificação", 1000);
        Objects.requireNonNull(verifiedAt, "instante da verificação");
        Objects.requireNonNull(verifiedBy, "responsável pela verificação");
    }
}
