package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

public record WaterSourceId(UUID value) {
    public WaterSourceId {
        Objects.requireNonNull(value, "id");
    }

    public static WaterSourceId newId() {
        return new WaterSourceId(UUID.randomUUID());
    }
}
