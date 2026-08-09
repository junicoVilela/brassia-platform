package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.SsoHandshake;
import java.util.Optional;

/** Persistência do aperto de mão SSO (SEC-B07). */
public interface SsoHandshakeRepository {

    void insert(SsoHandshake handshake);

    Optional<SsoHandshake> byState(String state);

    /**
     * Marca como consumido exigindo que ainda não estivesse.
     *
     * <p><strong>O uso único é decidido aqui, no banco, e não por uma verificação em memória.</strong> Duas
     * voltas simultâneas com a mesma resposta — um duplo clique, um retry do navegador — passariam as duas
     * por uma checagem feita em código antes de gravar. O {@code UPDATE ... WHERE consumed_at IS NULL}
     * deixa exatamente uma vencer.
     *
     * @return {@code false} quando outra requisição já o consumiu.
     */
    boolean markConsumed(SsoHandshake handshake);
}
