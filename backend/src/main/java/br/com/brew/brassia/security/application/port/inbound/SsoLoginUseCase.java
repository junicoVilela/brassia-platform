package br.com.brew.brassia.security.application.port.inbound;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/** Login federado iniciado pelo nosso lado (SEC-B07). */
public interface SsoLoginUseCase {

    /** Abre o aperto de mão e devolve para onde mandar o navegador. */
    Start start(StartCommand command);

    /** Confere a volta, resolve quem é a pessoa e devolve o usuário autenticado. */
    Completion complete(CallbackCommand command);

    record StartCommand(UUID breweryId, String providerCode, String redirectAfterLogin) {
    }

    record Start(URI authorizationUri, String state) {
    }

    /**
     * @param parameters o que o provedor devolveu — {@code code}, {@code state}, {@code SAMLResponse}…
     *                   Fica opaco de propósito: quem sabe interpretá-lo é o provedor concreto.
     */
    record CallbackCommand(String state, Map<String, String> parameters) {
    }

    /**
     * @param provisioned verdadeiro quando a conta foi criada agora (JIT). Viaja até a borda porque a
     *                    primeira entrada de alguém merece tratamento diferente da milésima.
     */
    record Completion(UUID userId, String redirectAfterLogin, boolean provisioned) {
    }
}
