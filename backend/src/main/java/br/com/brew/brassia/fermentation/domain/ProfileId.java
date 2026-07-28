package br.com.brew.brassia.fermentation.domain;

import java.util.Objects;
import java.util.UUID;

public record ProfileId(UUID value) {
    public ProfileId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ProfileId newId() {
        return new ProfileId(UUID.randomUUID());
    }
}
