package br.com.brew.brassia.container.domain;

/**
 * O que acontece com a caução quando o empréstimo termina.
 *
 * <p><strong>Isto é a decisão, e não o dinheiro.</strong> Devolver a caução é lançamento financeiro, e ele
 * mora onde o dinheiro mora — aqui fica registrado o que a operação decidiu, para o financeiro executar.
 * Fingir que o estorno acontece neste módulo faria o sistema afirmar um pagamento que ninguém fez.
 */
public enum DepositOutcome {
    /** O empréstimo está aberto: a caução continua retida. */
    HELD,
    /** O vasilhame voltou: a caução é devida ao cliente. */
    TO_REFUND,
    /** O vasilhame se perdeu: a caução fica com a casa. */
    RETAINED
}
