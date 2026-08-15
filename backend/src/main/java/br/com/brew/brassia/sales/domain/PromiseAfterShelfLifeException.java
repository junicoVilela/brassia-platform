package br.com.brew.brassia.sales.domain;

import java.time.LocalDate;

/**
 * A entrega foi prometida para depois de a cerveja vencer (SAL-002).
 *
 * <p>É a recusa que dá nome à história: "evitar promessa incompatível com lote/validade". Sem ela, o
 * pedido é aceito, o cliente organiza a operação dele em cima da data, e o problema aparece no dia da
 * carga — quando não há mais o que fazer além de desapontar alguém.
 *
 * <p>A data que manda é a do lote que vence primeiro, e não a média nem a maior: quem entrega tudo junto
 * entrega o mais velho junto.
 */
public class PromiseAfterShelfLifeException extends RuntimeException {

    private final LocalDate promisedFor;
    private final LocalDate earliestBestBefore;
    private final String lotCode;

    public PromiseAfterShelfLifeException(LocalDate promisedFor, LocalDate earliestBestBefore,
            String lotCode) {
        super("a entrega foi prometida para " + promisedFor + ", depois de o lote " + lotCode
                + " vencer em " + earliestBestBefore);
        this.promisedFor = promisedFor;
        this.earliestBestBefore = earliestBestBefore;
        this.lotCode = lotCode;
    }

    public LocalDate promisedFor() {
        return promisedFor;
    }

    public LocalDate earliestBestBefore() {
        return earliestBestBefore;
    }

    public String lotCode() {
        return lotCode;
    }
}
