package br.com.brew.brassia.equipment.domain;

import java.util.Objects;
import java.util.UUID;

public record MaintenanceId(UUID value) {
    public MaintenanceId {
        Objects.requireNonNull(value, "id");
    }

    public static MaintenanceId newId() {
        return new MaintenanceId(UUID.randomUUID());
    }
}
