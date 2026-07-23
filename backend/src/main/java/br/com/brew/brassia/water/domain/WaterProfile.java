package br.com.brew.brassia.water.domain;

import java.util.Objects;
import java.util.UUID;

/** Perfil mineral alvo (WTR-002): composição iônica desejada, por cervejaria. */
public final class WaterProfile {
    private final WaterProfileId id;
    private final UUID breweryId;
    private final WaterProfileCode code;
    private WaterProfileName name;
    private IonProfile targets;
    private final boolean active;
    private final long version;

    private WaterProfile(WaterProfileId id, UUID breweryId, WaterProfileCode code, WaterProfileName name,
            IonProfile targets, boolean active, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.targets = Objects.requireNonNull(targets);
        this.active = active;
        this.version = version;
    }

    public static WaterProfile register(UUID breweryId, WaterProfileCode code, WaterProfileName name,
            IonProfile targets) {
        return new WaterProfile(WaterProfileId.newId(), breweryId, code, name, targets, true, 0);
    }

    public static WaterProfile reconstitute(WaterProfileId id, UUID breweryId, WaterProfileCode code,
            WaterProfileName name, IonProfile targets, boolean active, long version) {
        return new WaterProfile(id, breweryId, code, name, targets, active, version);
    }

    public void update(WaterProfileName name, IonProfile targets) {
        this.name = Objects.requireNonNull(name);
        this.targets = Objects.requireNonNull(targets);
    }

    public WaterProfileId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public WaterProfileCode code() { return code; }
    public WaterProfileName name() { return name; }
    public IonProfile targets() { return targets; }
    public boolean active() { return active; }
    public long version() { return version; }
}
