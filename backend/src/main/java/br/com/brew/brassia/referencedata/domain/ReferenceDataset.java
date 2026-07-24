package br.com.brew.brassia.referencedata.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Versão datada de dados importados de uma {@link ReferenceSource}. Guarda o
 * payload bruto imutável, seu checksum e a proveniência. Só pode ser publicado se
 * a permissão da fonte autorizar ({@link PermissionStatus#allowsPublish()}); a
 * publicação é o gate de licença do REF-001. O ciclo de staging/validação é do REF-002.
 */
public final class ReferenceDataset {

    private final ReferenceDatasetId id;
    private final ReferenceSourceId sourceId;
    private final DatasetVersion version;
    private final Checksum checksum;
    private final Provenance provenance;
    private final String rawPayload;
    private final Instant effectiveFrom;
    private final Instant effectiveTo;
    private DatasetStatus status;
    private ReviewStatus reviewStatus;
    private Instant publishedAt;
    private final long optimisticVersion;

    private ReferenceDataset(ReferenceDatasetId id, ReferenceSourceId sourceId, DatasetVersion version,
            Checksum checksum, Provenance provenance, String rawPayload, Instant effectiveFrom, Instant effectiveTo,
            DatasetStatus status, ReviewStatus reviewStatus, Instant publishedAt, long optimisticVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.version = Objects.requireNonNull(version, "version");
        this.checksum = Objects.requireNonNull(checksum, "checksum");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        this.effectiveTo = effectiveTo;
        this.status = Objects.requireNonNull(status, "status");
        this.reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus");
        this.publishedAt = publishedAt;
        this.optimisticVersion = optimisticVersion;
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo não pode ser anterior a effectiveFrom");
        }
    }

    /** Cria um dataset em rascunho, aguardando revisão. */
    public static ReferenceDataset draft(ReferenceSourceId sourceId, DatasetVersion version, Checksum checksum,
            Provenance provenance, String rawPayload, Instant effectiveFrom, Instant effectiveTo) {
        return new ReferenceDataset(ReferenceDatasetId.newId(), sourceId, version, checksum, provenance, rawPayload,
                effectiveFrom, effectiveTo, DatasetStatus.DRAFT, ReviewStatus.PENDING, null, 1);
    }

    public static ReferenceDataset reconstitute(ReferenceDatasetId id, ReferenceSourceId sourceId,
            DatasetVersion version, Checksum checksum, Provenance provenance, String rawPayload, Instant effectiveFrom,
            Instant effectiveTo, DatasetStatus status, ReviewStatus reviewStatus, Instant publishedAt,
            long optimisticVersion) {
        return new ReferenceDataset(id, sourceId, version, checksum, provenance, rawPayload, effectiveFrom, effectiveTo,
                status, reviewStatus, publishedAt, optimisticVersion);
    }

    /**
     * Publica o dataset. Falha se a permissão da fonte não autorizar (gate de licença)
     * ou se já estiver publicado.
     *
     * @param sourcePermission permissão vigente da fonte proprietária
     * @param when instante da publicação (UTC)
     */
    public void publish(PermissionStatus sourcePermission, Instant when) {
        Objects.requireNonNull(sourcePermission, "sourcePermission");
        Objects.requireNonNull(when, "when");
        if (status == DatasetStatus.PUBLISHED) {
            throw new IllegalStateException("dataset já publicado");
        }
        if (!sourcePermission.allowsPublish()) {
            throw new IllegalStateException("permissão da fonte não autoriza publicação: " + sourcePermission);
        }
        this.status = DatasetStatus.PUBLISHED;
        this.reviewStatus = ReviewStatus.APPROVED;
        this.publishedAt = when;
    }

    public boolean isPublished() {
        return status == DatasetStatus.PUBLISHED;
    }

    public ReferenceDatasetId id() {
        return id;
    }

    public ReferenceSourceId sourceId() {
        return sourceId;
    }

    public DatasetVersion version() {
        return version;
    }

    public Checksum checksum() {
        return checksum;
    }

    public Provenance provenance() {
        return provenance;
    }

    public String rawPayload() {
        return rawPayload;
    }

    public Instant effectiveFrom() {
        return effectiveFrom;
    }

    public Instant effectiveTo() {
        return effectiveTo;
    }

    public DatasetStatus status() {
        return status;
    }

    public ReviewStatus reviewStatus() {
        return reviewStatus;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public long optimisticVersion() {
        return optimisticVersion;
    }
}
