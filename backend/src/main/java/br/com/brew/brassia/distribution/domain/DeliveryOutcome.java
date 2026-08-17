package br.com.brew.brassia.distribution.domain;

/**
 * O que aconteceu na parada.
 *
 * <p><strong>"Não entregue" não é um só motivo.</strong> Recusado, ninguém no local e remarcado levam a
 * ações diferentes no dia seguinte — juntá-los num "falhou" faria o roteirista tratar do mesmo jeito o
 * bar que rejeitou a mercadoria e o que estava fechado às sete da manhã.
 */
public enum DeliveryOutcome {
    DELIVERED,
    /** Parte desceu. O resto volta, e a carga precisa dizer o quê. */
    PARTIAL,
    /** O cliente recusou. */
    REFUSED,
    /** Ninguém no local. */
    ABSENT,
    /** Combinou-se outra data ali mesmo. */
    RESCHEDULED
}
