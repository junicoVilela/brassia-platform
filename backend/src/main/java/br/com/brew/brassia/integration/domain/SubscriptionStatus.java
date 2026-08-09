package br.com.brew.brassia.integration.domain;

/** Estado de uma assinatura de webhook (INT-002). */
public enum SubscriptionStatus {

    ACTIVE,

    /**
     * Pausada: novos eventos não são enfileirados para ela.
     *
     * <p>O que já está na fila <strong>continua</strong> sendo entregue. Pausar diz "pare de me mandar
     * coisa nova", não "esqueça o que já aconteceu" — descartar entregas pendentes faria uma pausa de
     * cinco minutos para manutenção perder eventos em silêncio.
     */
    PAUSED,

    /** Descontinuada. Terminal — não volta, e nada mais é entregue. */
    REVOKED
}
