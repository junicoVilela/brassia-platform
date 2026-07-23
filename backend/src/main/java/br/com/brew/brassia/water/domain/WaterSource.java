package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

/** Fonte de água da cervejaria (poço, rede pública, etc.). Multi-tenant. */
public final class WaterSource {
    private final WaterSourceId id;
    private final UUID breweryId;
    private final WaterSourceCode code;
    private WaterSourceName name;
    private final boolean active;
    private final long version;

    private WaterSource(WaterSourceId id, UUID breweryId, WaterSourceCode code, WaterSourceName name,
            boolean active, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.active = active;
        this.version = version;
    }

    public static WaterSource register(UUID breweryId, WaterSourceCode code, WaterSourceName name) {
        return new WaterSource(WaterSourceId.newId(), breweryId, code, name, true, 0);
    }

    public static WaterSource reconstitute(WaterSourceId id, UUID breweryId, WaterSourceCode code,
            WaterSourceName name, boolean active, long version) {
        return new WaterSource(id, breweryId, code, name, active, version);
    }

    public void rename(WaterSourceName name) {
        this.name = Objects.requireNonNull(name);
    }

    public WaterSourceId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public WaterSourceCode code() { return code; }
    public WaterSourceName name() { return name; }
    public boolean active() { return active; }
    public long version() { return version; }
}
