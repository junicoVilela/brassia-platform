package br.com.brew.brassia.packaging.domain;

/**
 * A expedição sairia com mais unidades do que o lote tem (TRC-001-D).
 *
 * <p>Recusar é o que mantém o recall honesto: a soma das expedições é o que ele usa para dizer
 * quanto está na rua, e um destino com unidades inventadas faria procurar caixas que nunca saíram.
 */
public final class ShipmentExceedsLotException extends RuntimeException {

    private final int lotUnits;
    private final int alreadyShipped;
    private final int requested;

    public ShipmentExceedsLotException(int lotUnits, int alreadyShipped, int requested) {
        super("expedição acima do que o lote tem");
        this.lotUnits = lotUnits;
        this.alreadyShipped = alreadyShipped;
        this.requested = requested;
    }

    public int lotUnits() {
        return lotUnits;
    }

    public int alreadyShipped() {
        return alreadyShipped;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return lotUnits - alreadyShipped;
    }
}
