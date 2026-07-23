package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

public record WaterReportId(UUID value) {
    public WaterReportId {
        Objects.requireNonNull(value, "id");
    }

    public static WaterReportId newId() {
        return new WaterReportId(UUID.randomUUID());
    }
}
