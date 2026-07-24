package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Fonte de dados de referência (REF-001): origem e condições de uso de qualquer
 * dado técnico incorporado ao catálogo. Pode ser global (curadoria BrassIA,
 * {@code breweryId == null}) ou privada de uma cervejaria (contribuição própria).
 * A licença/permissão da fonte é o gate que autoriza a publicação de seus datasets.
 */
public final class ReferenceSource {

    private final ReferenceSourceId id;
    private final UUID breweryId;
    private final SourceType type;
    private String name;
    private String owner;
    private String url;
    private LicenseInfo license;
    private String reviewFrequency;
    private String responsible;
    private final long version;

    private ReferenceSource(ReferenceSourceId id, UUID breweryId, SourceType type, String name, String owner,
            String url, LicenseInfo license, String reviewFrequency, String responsible, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = breweryId;
        this.type = Objects.requireNonNull(type, "type");
        this.name = requireText(name, "name");
        this.owner = requireText(owner, "owner");
        this.url = blankToNull(url);
        this.license = Objects.requireNonNull(license, "license");
        this.reviewFrequency = blankToNull(reviewFrequency);
        this.responsible = blankToNull(responsible);
        this.version = version;
    }

    /** Registra uma fonte. {@code breweryId} nulo indica fonte global (curadoria BrassIA). */
    public static ReferenceSource register(UUID breweryId, SourceType type, String name, String owner, String url,
            LicenseInfo license, String reviewFrequency, String responsible) {
        return new ReferenceSource(ReferenceSourceId.newId(), breweryId, type, name, owner, url, license,
                reviewFrequency, responsible, 1);
    }

    public static ReferenceSource reconstitute(ReferenceSourceId id, UUID breweryId, SourceType type, String name,
            String owner, String url, LicenseInfo license, String reviewFrequency, String responsible, long version) {
        return new ReferenceSource(id, breweryId, type, name, owner, url, license, reviewFrequency, responsible,
                version);
    }

    public boolean isGlobal() {
        return breweryId == null;
    }

    public PermissionStatus permissionStatus() {
        return license.permissionStatus();
    }

    public boolean allowsPublish() {
        return license.allowsPublish();
    }

    public ReferenceSourceId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public SourceType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String owner() {
        return owner;
    }

    public String url() {
        return url;
    }

    public LicenseInfo license() {
        return license;
    }

    public String reviewFrequency() {
        return reviewFrequency;
    }

    public String responsible() {
        return responsible;
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
