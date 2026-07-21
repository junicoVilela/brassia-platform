package br.com.brew.brassia.brewery.domain;

import java.util.Objects;

/** Cervejaria — o tenant do sistema. Agregado pequeno; preferências são BRW-002. */
public final class Brewery {
    private final BreweryId id;
    private final BreweryCode code;
    private BreweryName name;
    private Timezone timezone;
    private long version;

    private Brewery(BreweryId id, BreweryCode code, BreweryName name, Timezone timezone, long version) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.timezone = Objects.requireNonNull(timezone);
        this.version = version;
    }

    public static Brewery register(BreweryCode code, BreweryName name, Timezone timezone) {
        return new Brewery(BreweryId.newId(), code, name, timezone, 0);
    }

    public static Brewery reconstitute(BreweryId id, BreweryCode code, BreweryName name,
            Timezone timezone, long version) {
        return new Brewery(id, code, name, timezone, version);
    }

    public BreweryId id() { return id; }
    public BreweryCode code() { return code; }
    public BreweryName name() { return name; }
    public Timezone timezone() { return timezone; }
    public long version() { return version; }
}
