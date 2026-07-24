package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

public record WaterReferenceProfileId(UUID value) {
    public WaterReferenceProfileId {
        Objects.requireNonNull(value, "value is required");
    }

    public static WaterReferenceProfileId newId() {
        return new WaterReferenceProfileId(UUID.randomUUID());
    }
}
