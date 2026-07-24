package br.com.brew.brassia.referencedata.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Conjunto versionado de estilos de uma autoridade/edição (STD-001). Guarda
 * autoridade, edição, idioma, vigência, atribuição e nível de permissão. Pode ser
 * global (curadoria) ou da cervejaria (perfil próprio/competição). A publicação
 * respeita o gate de licença; receitas publicadas preservam o snapshot do estilo.
 */
public final class StyleSet {

    private final StyleSetId id;
    private final UUID breweryId;
    private final ReferenceSourceId sourceId;
    private final StyleAuthority authority;
    private final String edition;
    private final String language;
    private final Instant effectiveFrom;
    private final Instant effectiveTo;
    private final String attribution;
    private final PermissionStatus permissionStatus;
    private DatasetStatus status;
    private Instant publishedAt;
    private final List<Style> styles;
    private final long version;

    private StyleSet(StyleSetId id, UUID breweryId, ReferenceSourceId sourceId, StyleAuthority authority,
            String edition, String language, Instant effectiveFrom, Instant effectiveTo, String attribution,
            PermissionStatus permissionStatus, DatasetStatus status, Instant publishedAt, List<Style> styles,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = breweryId;
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.edition = requireText(edition, "edition");
        this.language = requireText(language, "language");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        this.effectiveTo = effectiveTo;
        this.attribution = blankToNull(attribution);
        this.permissionStatus = Objects.requireNonNull(permissionStatus, "permissionStatus");
        this.status = Objects.requireNonNull(status, "status");
        this.publishedAt = publishedAt;
        this.styles = List.copyOf(Objects.requireNonNull(styles, "styles"));
        this.version = version;
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo não pode ser anterior a effectiveFrom");
        }
    }

    public static StyleSet draft(UUID breweryId, ReferenceSourceId sourceId, StyleAuthority authority, String edition,
            String language, Instant effectiveFrom, Instant effectiveTo, String attribution,
            PermissionStatus permissionStatus, List<Style> styles) {
        return new StyleSet(StyleSetId.newId(), breweryId, sourceId, authority, edition, language, effectiveFrom,
                effectiveTo, attribution, permissionStatus, DatasetStatus.DRAFT, null, styles, 1);
    }

    public static StyleSet reconstitute(StyleSetId id, UUID breweryId, ReferenceSourceId sourceId,
            StyleAuthority authority, String edition, String language, Instant effectiveFrom, Instant effectiveTo,
            String attribution, PermissionStatus permissionStatus, DatasetStatus status, Instant publishedAt,
            List<Style> styles, long version) {
        return new StyleSet(id, breweryId, sourceId, authority, edition, language, effectiveFrom, effectiveTo,
                attribution, permissionStatus, status, publishedAt, styles, version);
    }

    /** Publica o conjunto: {@code DRAFT → PUBLISHED}, respeitando o gate de licença. */
    public void publish(Instant when) {
        Objects.requireNonNull(when, "when");
        if (status == DatasetStatus.PUBLISHED) {
            throw new IllegalStateException("conjunto já publicado");
        }
        if (!permissionStatus.allowsPublish()) {
            throw new IllegalStateException("permissão da fonte não autoriza publicação: " + permissionStatus);
        }
        this.status = DatasetStatus.PUBLISHED;
        this.publishedAt = when;
    }

    public boolean isGlobal() {
        return breweryId == null;
    }

    public boolean isPublished() {
        return status == DatasetStatus.PUBLISHED;
    }

    public StyleSetId id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public ReferenceSourceId sourceId() {
        return sourceId;
    }

    public StyleAuthority authority() {
        return authority;
    }

    public String edition() {
        return edition;
    }

    public String language() {
        return language;
    }

    public Instant effectiveFrom() {
        return effectiveFrom;
    }

    public Instant effectiveTo() {
        return effectiveTo;
    }

    public String attribution() {
        return attribution;
    }

    public PermissionStatus permissionStatus() {
        return permissionStatus;
    }

    public DatasetStatus status() {
        return status;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public List<Style> styles() {
        return styles;
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
