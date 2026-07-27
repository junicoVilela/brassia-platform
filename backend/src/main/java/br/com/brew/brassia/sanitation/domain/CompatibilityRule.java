package br.com.brew.brassia.sanitation.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Regra da matriz de compatibilidade (CLN-002): recomenda um método/POP por
 * material, sujidade, risco e produto anterior. Sem herança entre materiais —
 * madeira/plástico não herdam inox. A alternativa explica restrições.
 */
public final class CompatibilityRule {

    private final UUID id;
    private final UUID breweryId;
    private final EquipmentMaterial material;
    private final SoilingLevel soiling;
    private final RiskLevel risk;
    private final String previousProduct;
    private final String procedureCode;
    private final String method;
    private final String alternative;
    private final String restriction;

    private CompatibilityRule(UUID id, UUID breweryId, EquipmentMaterial material, SoilingLevel soiling,
            RiskLevel risk, String previousProduct, String procedureCode, String method, String alternative,
            String restriction) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.material = Objects.requireNonNull(material, "material");
        this.soiling = Objects.requireNonNull(soiling, "soiling");
        this.risk = Objects.requireNonNull(risk, "risk");
        this.previousProduct = normalize(previousProduct);
        this.procedureCode = trimToNull(procedureCode);
        this.method = requireText(method);
        this.alternative = trimToNull(alternative);
        this.restriction = trimToNull(restriction);
    }

    public static CompatibilityRule create(UUID breweryId, EquipmentMaterial material, SoilingLevel soiling,
            RiskLevel risk, String previousProduct, String procedureCode, String method, String alternative,
            String restriction) {
        return new CompatibilityRule(UUID.randomUUID(), breweryId, material, soiling, risk, previousProduct,
                procedureCode, method, alternative, restriction);
    }

    public static CompatibilityRule reconstitute(UUID id, UUID breweryId, EquipmentMaterial material,
            SoilingLevel soiling, RiskLevel risk, String previousProduct, String procedureCode, String method,
            String alternative, String restriction) {
        return new CompatibilityRule(id, breweryId, material, soiling, risk, previousProduct, procedureCode, method,
                alternative, restriction);
    }

    /** Produto anterior normalizado (minúsculo, sem espaços nas pontas) ou nulo (regra genérica). */
    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("método recomendado é obrigatório");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public EquipmentMaterial material() { return material; }
    public SoilingLevel soiling() { return soiling; }
    public RiskLevel risk() { return risk; }
    public String previousProduct() { return previousProduct; }
    public String procedureCode() { return procedureCode; }
    public String method() { return method; }
    public String alternative() { return alternative; }
    public String restriction() { return restriction; }
}
