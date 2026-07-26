package br.com.brew.brassia.planning.domain;

import java.util.Objects;
import java.util.UUID;

public record ScheduleEntryId(UUID value) {
    public ScheduleEntryId {
        Objects.requireNonNull(value, "value is required");
    }

    public static ScheduleEntryId newId() {
        return new ScheduleEntryId(UUID.randomUUID());
    }
}
