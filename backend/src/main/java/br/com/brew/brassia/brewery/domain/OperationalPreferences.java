package br.com.brew.brassia.brewery.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Preferências operacionais vigentes da cervejaria. Mutáveis com optimistic
 * locking; cada alteração gera uma revisão imutável (snapshot) para que
 * consumidores futuros não reinterpretam o passado.
 */
public final class OperationalPreferences {
    private static final Set<String> VOLUME = Set.of("L", "ML");
    private static final Set<String> MASS = Set.of("KG", "G");
    private static final Set<String> TEMP = Set.of("C", "F");
    private static final Set<String> STOCK = Set.of("FEFO", "FIFO", "NONE");

    private final UUID breweryId;
    private String volumeUnit;
    private String massUnit;
    private String temperatureUnit;
    private String currencyCode;
    private BigDecimal maxBatchVolume;
    private boolean allowNegativeStock;
    private String stockPolicy;
    private long version;

    private OperationalPreferences(UUID breweryId) {
        this.breweryId = Objects.requireNonNull(breweryId);
    }

    public static OperationalPreferences defaults(UUID breweryId) {
        var prefs = new OperationalPreferences(breweryId);
        prefs.volumeUnit = "L";
        prefs.massUnit = "KG";
        prefs.temperatureUnit = "C";
        prefs.currencyCode = "BRL";
        prefs.maxBatchVolume = new BigDecimal("1000");
        prefs.allowNegativeStock = false;
        prefs.stockPolicy = "FEFO";
        prefs.version = 0;
        return prefs;
    }

    public static OperationalPreferences reconstitute(
            UUID breweryId,
            String volumeUnit,
            String massUnit,
            String temperatureUnit,
            String currencyCode,
            BigDecimal maxBatchVolume,
            boolean allowNegativeStock,
            String stockPolicy,
            long version) {
        var prefs = new OperationalPreferences(breweryId);
        prefs.volumeUnit = require(VOLUME, volumeUnit, "volumeUnit");
        prefs.massUnit = require(MASS, massUnit, "massUnit");
        prefs.temperatureUnit = require(TEMP, temperatureUnit, "temperatureUnit");
        prefs.currencyCode = requireCurrency(currencyCode);
        prefs.maxBatchVolume = requirePositive(maxBatchVolume);
        prefs.allowNegativeStock = allowNegativeStock;
        prefs.stockPolicy = require(STOCK, stockPolicy, "stockPolicy");
        prefs.version = version;
        return prefs;
    }

    public void update(
            String volumeUnit,
            String massUnit,
            String temperatureUnit,
            String currencyCode,
            BigDecimal maxBatchVolume,
            boolean allowNegativeStock,
            String stockPolicy,
            long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new IllegalStateException("versão das preferências divergiu");
        }
        this.volumeUnit = require(VOLUME, volumeUnit, "volumeUnit");
        this.massUnit = require(MASS, massUnit, "massUnit");
        this.temperatureUnit = require(TEMP, temperatureUnit, "temperatureUnit");
        this.currencyCode = requireCurrency(currencyCode);
        this.maxBatchVolume = requirePositive(maxBatchVolume);
        this.allowNegativeStock = allowNegativeStock;
        this.stockPolicy = require(STOCK, stockPolicy, "stockPolicy");
        this.version = expectedVersion + 1;
    }

    /** Snapshot imutável da versão corrente (para materializar em ordens/lotes futuros). */
    public OperationalPreferencesSnapshot snapshot() {
        return new OperationalPreferencesSnapshot(
                breweryId, version, volumeUnit, massUnit, temperatureUnit, currencyCode,
                maxBatchVolume, allowNegativeStock, stockPolicy);
    }

    public UUID breweryId() { return breweryId; }
    public String volumeUnit() { return volumeUnit; }
    public String massUnit() { return massUnit; }
    public String temperatureUnit() { return temperatureUnit; }
    public String currencyCode() { return currencyCode; }
    public BigDecimal maxBatchVolume() { return maxBatchVolume; }
    public boolean allowNegativeStock() { return allowNegativeStock; }
    public String stockPolicy() { return stockPolicy; }
    public long version() { return version; }

    private static String require(Set<String> allowed, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " obrigatório");
        }
        var value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " inválido");
        }
        return value;
    }

    private static String requireCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("currencyCode obrigatório");
        }
        var value = raw.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currencyCode inválido");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("maxBatchVolume deve ser positivo");
        }
        return value;
    }
}
