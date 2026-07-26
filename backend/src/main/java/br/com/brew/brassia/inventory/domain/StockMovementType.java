package br.com.brew.brassia.inventory.domain;

/**
 * Tipos de movimento do ledger de estoque (STK-002). Cada tipo define como afeta
 * as dimensões de saldo: on-hand (físico) e reservado. Disponível = on-hand −
 * reservado. Movimentos são imutáveis; o saldo deriva da soma dos deltas.
 */
public enum StockMovementType {
    ENTRY(1, 0),
    CONSUMPTION(-1, 0),
    RETURN(1, 0),
    LOSS(-1, 0),
    ADJUSTMENT_IN(1, 0),
    ADJUSTMENT_OUT(-1, 0),
    RESERVATION(0, 1),
    RELEASE(0, -1);

    private final int onHandSign;
    private final int reservedSign;

    StockMovementType(int onHandSign, int reservedSign) {
        this.onHandSign = onHandSign;
        this.reservedSign = reservedSign;
    }

    public int onHandSign() {
        return onHandSign;
    }

    public int reservedSign() {
        return reservedSign;
    }

    /** Tipos que reduzem o on-hand (saídas físicas), sujeitos à guarda de saldo. */
    public boolean isOutflow() {
        return onHandSign < 0;
    }

    /** Movimentos de ajuste exigem motivo (regra de negócio 4). */
    public boolean requiresReason() {
        return this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT;
    }
}
