package br.com.brew.brassia.sales.domain;

/**
 * O item promete mais do que reservou (SAL-002).
 *
 * <p>Um item de 100 unidades com 80 reservadas é promessa que a cervejaria não pode cumprir — e o pior
 * momento para descobrir isso é na expedição, com o caminhão parado. O pedido é recusado inteiro: aceitar
 * parcialmente faria o cliente receber uma confirmação de algo que ninguém conseguiu segurar.
 */
public class UnreservedQuantityException extends RuntimeException {

    private final int requested;
    private final int reserved;

    public UnreservedQuantityException(String sku, int requested, int reserved) {
        super("o item " + sku + " pede " + requested + " unidade(s) e só tem " + reserved + " reservada(s)");
        this.requested = requested;
        this.reserved = reserved;
    }

    public int requested() {
        return requested;
    }

    public int reserved() {
        return reserved;
    }
}
