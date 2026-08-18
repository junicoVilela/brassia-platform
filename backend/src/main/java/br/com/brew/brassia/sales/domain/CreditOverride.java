package br.com.brew.brassia.sales.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A autorização de um pedido acima do teto de crédito (SAL-004).
 *
 * <p><strong>Ela existe para o teto não virar decoração.</strong> No portal a recusa é a única resposta
 * honesta — não há vendedor por perto para explicar. Na porta interna há uma pessoa que sabe coisas que o
 * sistema não sabe: o pagamento que cai hoje, o acordo de ontem, o cliente de dez anos. Recusar duro faria
 * essa pessoa cadastrar um teto maior "só por hoje" e esquecer de voltar — e aí o limite deixa de existir
 * para sempre, em vez de por um pedido.
 *
 * <p><strong>Tudo ou nada.</strong> Motivo sem autor, ou autor sem data, é um registro que não responde a
 * pergunta para a qual ele existe: quem deixou passar, quando, e por quê.
 */
public record CreditOverride(String reason, UUID authorizedBy, Instant authorizedAt) {

    public CreditOverride {
        if (reason == null || reason.isBlank()) {
            // "Autorizado" sem motivo é a mesma coisa que não ter teto: ninguém consegue julgar depois se
            // a exceção foi razoável.
            throw new IllegalArgumentException("a autorização acima do teto precisa de motivo");
        }
        Objects.requireNonNull(authorizedBy, "quem autorizou");
        Objects.requireNonNull(authorizedAt, "quando autorizou");
        reason = reason.trim();
    }
}
