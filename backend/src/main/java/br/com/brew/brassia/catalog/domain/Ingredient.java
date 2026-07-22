package br.com.brew.brassia.catalog.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ingrediente do catálogo (CAT-001). Agregado pequeno, multi-tenant por
 * {@code breweryId}. Os atributos específicos são validados contra o
 * {@link IngredientType}: chave não permitida ou valor vazio é rejeitado.
 */
public final class Ingredient {
    private final IngredientId id;
    private final UUID breweryId;
    private final IngredientType type;
    private final IngredientCode code;
    private IngredientName name;
    private MeasurementUnit useUnit;
    private MeasurementUnit purchaseUnit;
    private Map<String, String> attributes;
    private final boolean active;
    private final long version;

    private Ingredient(IngredientId id, UUID breweryId, IngredientType type, IngredientCode code,
            IngredientName name, MeasurementUnit useUnit, MeasurementUnit purchaseUnit,
            Map<String, String> attributes, boolean active, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.type = Objects.requireNonNull(type);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.useUnit = Objects.requireNonNull(useUnit);
        this.purchaseUnit = Objects.requireNonNull(purchaseUnit);
        this.attributes = validateAttributes(type, attributes);
        this.active = active;
        this.version = version;
    }

    public static Ingredient register(UUID breweryId, IngredientType type, IngredientCode code,
            IngredientName name, MeasurementUnit useUnit, MeasurementUnit purchaseUnit,
            Map<String, String> attributes) {
        return new Ingredient(IngredientId.newId(), breweryId, type, code, name, useUnit, purchaseUnit,
                attributes, true, 0);
    }

    public static Ingredient reconstitute(IngredientId id, UUID breweryId, IngredientType type,
            IngredientCode code, IngredientName name, MeasurementUnit useUnit, MeasurementUnit purchaseUnit,
            Map<String, String> attributes, boolean active, long version) {
        return new Ingredient(id, breweryId, type, code, name, useUnit, purchaseUnit, attributes, active, version);
    }

    /** Atualiza os campos mutáveis; tipo e código são imutáveis após o cadastro. */
    public void update(IngredientName name, MeasurementUnit useUnit, MeasurementUnit purchaseUnit,
            Map<String, String> attributes) {
        this.name = Objects.requireNonNull(name);
        this.useUnit = Objects.requireNonNull(useUnit);
        this.purchaseUnit = Objects.requireNonNull(purchaseUnit);
        this.attributes = validateAttributes(this.type, attributes);
    }

    private static Map<String, String> validateAttributes(IngredientType type, Map<String, String> attributes) {
        var result = new LinkedHashMap<String, String>();
        if (attributes == null) {
            return result;
        }
        for (var entry : attributes.entrySet()) {
            var key = entry.getKey();
            if (!type.allowedAttributes().contains(key)) {
                throw new IllegalArgumentException("atributo não permitido para o tipo " + type + ": " + key);
            }
            var value = entry.getValue();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("atributo sem valor: " + key);
            }
            result.put(key, value.trim());
        }
        return result;
    }

    public IngredientId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public IngredientType type() { return type; }
    public IngredientCode code() { return code; }
    public IngredientName name() { return name; }
    public MeasurementUnit useUnit() { return useUnit; }
    public MeasurementUnit purchaseUnit() { return purchaseUnit; }
    public Map<String, String> attributes() { return Map.copyOf(attributes); }
    public boolean active() { return active; }
    public long version() { return version; }
}
