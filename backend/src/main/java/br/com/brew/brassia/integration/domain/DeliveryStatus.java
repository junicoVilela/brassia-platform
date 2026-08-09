package br.com.brew.brassia.integration.domain;

/** Estado de uma entrega no outbox (INT-002). */
public enum DeliveryStatus {

    /** Enfileirada, aguardando a próxima janela de tentativa. */
    PENDING,

    DELIVERED,

    /**
     * Tentativas esgotadas.
     *
     * <p>Terminal e <strong>não apagada</strong>. Uma entrega que desiste em silêncio é a pior forma de
     * falha de integração: o outro lado nunca soube do evento, e nós também não sabemos que ele não soube.
     * A linha fica, com o último erro e a contagem, para quem for investigar por que o sistema do cliente
     * está desatualizado.
     */
    EXHAUSTED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
