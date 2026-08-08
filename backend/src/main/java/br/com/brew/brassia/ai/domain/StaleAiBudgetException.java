package br.com.brew.brassia.ai.domain;

/**
 * Alguém alterou o teto de gasto entre a leitura e a gravação (AIA-001).
 *
 * <p>A escrita é recusada em vez de vencer: teto de gasto é decisão, e sobrescrever a decisão de
 * outra pessoa sem que ela saiba é pior do que pedir para ela tentar de novo.
 */
public final class StaleAiBudgetException extends RuntimeException {

    public StaleAiBudgetException() {
        super("o orçamento de IA foi alterado por outra pessoa; recarregue e tente novamente");
    }
}
