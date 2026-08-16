package br.com.brew.brassia.container.domain;

/**
 * De quem é o vasilhame.
 *
 * <p>Importa porque o retornável de terceiro <strong>não é ativo da cervejaria</strong> — e tratar os
 * três como iguais faria o inventário contar como patrimônio o que é do cliente, e cobrar depósito de
 * quem já é dono do próprio keg.
 */
public enum Ownership {
    /** Da casa. */
    OWN,
    /** Do cliente — ele mandou encher o dele. */
    CUSTOMER,
    /** De um pool compartilhado, em comodato. */
    POOL
}
