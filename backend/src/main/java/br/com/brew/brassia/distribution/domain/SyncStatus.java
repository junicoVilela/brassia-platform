package br.com.brew.brassia.distribution.domain;

/**
 * O que aconteceu com uma operação que veio do aparelho.
 *
 * <p><strong>Quatro estados, e nenhum deles é silêncio.</strong> O entregador precisa saber, ao olhar o
 * celular, se o que ele digitou no subsolo do bar chegou — e "sincronizado" sozinho não distingue o que
 * foi aplicado do que foi recusado.
 */
public enum SyncStatus {
    /** Entrou. */
    APPLIED,
    /** Já tinha entrado antes: o reenvio devolve o mesmo resultado, e não cria outro. */
    DUPLICATE,
    /** O servidor mudou embaixo: alguém decide, e o aparelho não sobrescreve. */
    CONFLICTED,
    /** Não pôde entrar — e o motivo fica. */
    REJECTED
}
