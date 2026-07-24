package br.com.brew.brassia.catalog.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil técnico de referência de um ingrediente (CAT-003): metadados de
 * fabricante/laboratório, faixas de propriedades e descritores, com proveniência
 * (fonte). O catálogo guarda faixas — valores por safra/lote pertencem ao estoque.
 * Só perfis publicados devem alimentar cálculo/receita ("quando publicados").
 */
public final class IngredientTechnicalProfile {

    private final TechnicalProfileId id;
    private final UUID breweryId;
    private final UUID ingredientId;
    private final String manufacturer;
    private final String origin;
    private final String form;
    private final String purpose;
    private final String laboratory;
    private final String labCode;
    private final Map<String, PropertyRange> ranges;
    private final List<String> descriptors;
    private final UUID sourceId;
    private final String sourceName;
    private TechnicalProfileStatus status;
    private final long version;

    private IngredientTechnicalProfile(TechnicalProfileId id, UUID breweryId, UUID ingredientId, String manufacturer,
            String origin, String form, String purpose, String laboratory, String labCode,
            Map<String, PropertyRange> ranges, List<String> descriptors, UUID sourceId, String sourceName,
            TechnicalProfileStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId");
        this.manufacturer = blankToNull(manufacturer);
        this.origin = blankToNull(origin);
        this.form = blankToNull(form);
        this.purpose = blankToNull(purpose);
        this.laboratory = blankToNull(laboratory);
        this.labCode = blankToNull(labCode);
        this.ranges = sanitizeRanges(ranges);
        this.descriptors = sanitizeDescriptors(descriptors);
        this.sourceId = sourceId;
        this.sourceName = blankToNull(sourceName);
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public static IngredientTechnicalProfile draft(UUID breweryId, UUID ingredientId, String manufacturer,
            String origin, String form, String purpose, String laboratory, String labCode,
            Map<String, PropertyRange> ranges, List<String> descriptors, UUID sourceId, String sourceName) {
        return new IngredientTechnicalProfile(TechnicalProfileId.newId(), breweryId, ingredientId, manufacturer,
                origin, form, purpose, laboratory, labCode, ranges, descriptors, sourceId, sourceName,
                TechnicalProfileStatus.DRAFT, 1);
    }

    public static IngredientTechnicalProfile reconstitute(TechnicalProfileId id, UUID breweryId, UUID ingredientId,
            String manufacturer, String origin, String form, String purpose, String laboratory, String labCode,
            Map<String, PropertyRange> ranges, List<String> descriptors, UUID sourceId, String sourceName,
            TechnicalProfileStatus status, long version) {
        return new IngredientTechnicalProfile(id, breweryId, ingredientId, manufacturer, origin, form, purpose,
                laboratory, labCode, ranges, descriptors, sourceId, sourceName, status, version);
    }

    /** Publica o perfil após revisão: {@code DRAFT → PUBLISHED}. */
    public void publish() {
        if (status == TechnicalProfileStatus.PUBLISHED) {
            throw new IllegalStateException("perfil já publicado");
        }
        this.status = TechnicalProfileStatus.PUBLISHED;
    }

    public boolean isPublished() {
        return status == TechnicalProfileStatus.PUBLISHED;
    }

    public TechnicalProfileId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID ingredientId() {
        return ingredientId;
    }

    public String manufacturer() {
        return manufacturer;
    }

    public String origin() {
        return origin;
    }

    public String form() {
        return form;
    }

    public String purpose() {
        return purpose;
    }

    public String laboratory() {
        return laboratory;
    }

    public String labCode() {
        return labCode;
    }

    public Map<String, PropertyRange> ranges() {
        return ranges;
    }

    public List<String> descriptors() {
        return descriptors;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public String sourceName() {
        return sourceName;
    }

    public TechnicalProfileStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    private static Map<String, PropertyRange> sanitizeRanges(Map<String, PropertyRange> ranges) {
        var result = new LinkedHashMap<String, PropertyRange>();
        if (ranges != null) {
            ranges.forEach((key, range) -> {
                if (key != null && !key.isBlank() && range != null && !range.isEmpty()) {
                    result.put(key.trim(), range);
                }
            });
        }
        return Map.copyOf(result);
    }

    private static List<String> sanitizeDescriptors(List<String> descriptors) {
        if (descriptors == null) {
            return List.of();
        }
        return descriptors.stream().filter(d -> d != null && !d.isBlank()).map(String::trim).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
