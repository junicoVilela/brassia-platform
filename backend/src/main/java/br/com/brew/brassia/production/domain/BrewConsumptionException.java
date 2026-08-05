package br.com.brew.brassia.production.domain;

import br.com.brew.brassia.production.ProductionStockGateway.Shortfall;
import java.util.List;
import java.util.Objects;

/**
 * O consumo declarado não cabe no estoque do lote (TRC-001-C).
 *
 * <p>A falta acompanha a recusa lote a lote, porque a correção é do operador: ou ele digitou a
 * quantidade errada, ou usou um lote diferente do que está dizendo. Adivinhar qual dos dois seria
 * inventar produção.
 */
public final class BrewConsumptionException extends RuntimeException {

    private final transient List<Shortfall> shortfalls;

    public BrewConsumptionException(List<Shortfall> shortfalls) {
        super("consumo acima do que o lote tem");
        this.shortfalls = List.copyOf(Objects.requireNonNull(shortfalls));
    }

    public List<Shortfall> shortfalls() {
        return shortfalls;
    }
}
