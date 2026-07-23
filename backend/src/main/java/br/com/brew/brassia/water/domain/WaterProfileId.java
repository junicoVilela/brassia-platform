package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

public record WaterProfileId(UUID value) {
    public WaterProfileId {
        Objects.requireNonNull(value, "id");
    }

    public static WaterProfileId newId() {
        return new WaterProfileId(UUID.randomUUID());
    }
}
