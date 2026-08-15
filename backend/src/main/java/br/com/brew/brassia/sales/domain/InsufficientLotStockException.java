package br.com.brew.brassia.sales.domain;

/**
 * O lote não tem unidades suficientes para a reserva (SAL-002).
 *
 * <p><strong>Também é a resposta de quem perdeu a corrida.</strong> Duas requisições simultâneas
 * disputam a mesma linha de disponibilidade; a segunda relê o valor já atualizado e descobre que não
 * cabe. Do ponto de vista de quem chamou os dois casos são o mesmo — o estoque acabou entre olhar e
 * pedir —, e é por isso que a mensagem fala de unidades e não de concorrência.
 */
public class InsufficientLotStockException extends RuntimeException {

    private final String lotCode;
    private final int requested;
    private final int available;

    public InsufficientLotStockException(String lotCode, int requested, int available) {
        super("o lote " + lotCode + " tem " + available + " unidade(s) livre(s) e foram pedidas "
                + requested);
        this.lotCode = lotCode;
        this.requested = requested;
        this.available = available;
    }

    public String lotCode() {
        return lotCode;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
