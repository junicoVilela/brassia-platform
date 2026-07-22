package br.com.brew.brassia.equipment.domain;

import java.util.Objects;
import java.util.UUID;

public record EquipmentId(UUID value) {
    public EquipmentId {
        Objects.requireNonNull(value, "id");
    }

    public static EquipmentId newId() {
        return new EquipmentId(UUID.randomUUID());
    }
}
