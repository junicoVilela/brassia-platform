package br.com.brew.brassia.security.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "user id");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }
}
