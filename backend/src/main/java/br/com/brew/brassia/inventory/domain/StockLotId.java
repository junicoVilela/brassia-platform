package br.com.brew.brassia.inventory.domain;

import java.util.Objects;
import java.util.UUID;

public record StockLotId(UUID value) {
    public StockLotId {
        Objects.requireNonNull(value, "value is required");
    }

    public static StockLotId newId() {
        return new StockLotId(UUID.randomUUID());
    }
}
