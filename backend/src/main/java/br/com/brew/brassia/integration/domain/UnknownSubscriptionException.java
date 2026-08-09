package br.com.brew.brassia.integration.domain;

import java.util.UUID;

/**
 * Assinatura inexistente nesta cervejaria (INT-002).
 *
 * <p>Mesma resposta para "não existe" e "é de outra cervejaria": distinguir as duas contaria que ela
 * existe em algum lugar.
 */
public class UnknownSubscriptionException extends RuntimeException {

    private final UUID subscriptionId;

    public UnknownSubscriptionException(UUID subscriptionId) {
        super("assinatura desconhecida: " + subscriptionId);
        this.subscriptionId = subscriptionId;
    }

    public UUID subscriptionId() {
        return subscriptionId;
    }
}
