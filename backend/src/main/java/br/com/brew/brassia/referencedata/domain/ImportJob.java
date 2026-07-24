package br.com.brew.brassia.referencedata.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Job de importação de dados de referência (REF-002): recebe um payload bruto,
 * é validado (schema/tamanho/MIME/duplicidade) e, se aprovado, publica um
 * {@link ReferenceDataset}. Máquina de estado:
 * {@code RECEIVED → VALIDATING → REVIEW_REQUIRED → PUBLISHED} ou {@code FAILED}.
 * Não contamina o catálogo: só a publicação materializa o dataset.
 */
public final class ImportJob {

    private final ImportJobId id;
    private final ReferenceSourceId sourceId;
    private final UUID breweryId;
    private final String datasetVersion;
    private final String contentType;
    private final long sizeBytes;
    private final Checksum checksum;
    private final String rawPayload;
    private ImportJobStatus status;
    private final List<ValidationIssue> issues;
    private ReferenceDatasetId publishedDatasetId;
    private final long optimisticVersion;

    private ImportJob(ImportJobId id, ReferenceSourceId sourceId, UUID breweryId, String datasetVersion,
            String contentType, long sizeBytes, Checksum checksum, String rawPayload, ImportJobStatus status,
            List<ValidationIssue> issues, ReferenceDatasetId publishedDatasetId, long optimisticVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.breweryId = breweryId;
        this.datasetVersion = Objects.requireNonNull(datasetVersion, "datasetVersion");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.checksum = Objects.requireNonNull(checksum, "checksum");
        this.rawPayload = Objects.requireNonNull(rawPayload, "rawPayload");
        this.status = Objects.requireNonNull(status, "status");
        this.issues = new ArrayList<>(Objects.requireNonNull(issues, "issues"));
        this.publishedDatasetId = publishedDatasetId;
        this.optimisticVersion = optimisticVersion;
    }

    /** Recebe um novo job; começa em {@code RECEIVED}, sem problemas registrados. */
    public static ImportJob receive(ReferenceSourceId sourceId, UUID breweryId, String datasetVersion,
            String contentType, long sizeBytes, Checksum checksum, String rawPayload) {
        return new ImportJob(ImportJobId.newId(), sourceId, breweryId, datasetVersion, contentType, sizeBytes,
                checksum, rawPayload, ImportJobStatus.RECEIVED, List.of(), null, 1);
    }

    public static ImportJob reconstitute(ImportJobId id, ReferenceSourceId sourceId, UUID breweryId,
            String datasetVersion, String contentType, long sizeBytes, Checksum checksum, String rawPayload,
            ImportJobStatus status, List<ValidationIssue> issues, ReferenceDatasetId publishedDatasetId,
            long optimisticVersion) {
        return new ImportJob(id, sourceId, breweryId, datasetVersion, contentType, sizeBytes, checksum, rawPayload,
                status, issues, publishedDatasetId, optimisticVersion);
    }

    /** Inicia a validação: {@code RECEIVED → VALIDATING}. */
    public void startValidation() {
        if (status != ImportJobStatus.RECEIVED) {
            throw new IllegalStateException("job precisa estar em RECEIVED para validar");
        }
        this.status = ImportJobStatus.VALIDATING;
    }

    /**
     * Conclui a validação registrando os problemas encontrados. Qualquer erro leva
     * a {@code FAILED}; apenas avisos (ou nenhum) levam a {@code REVIEW_REQUIRED}.
     */
    public void recordValidation(List<ValidationIssue> found) {
        if (status != ImportJobStatus.VALIDATING) {
            throw new IllegalStateException("job precisa estar em VALIDATING para concluir a validação");
        }
        this.issues.addAll(Objects.requireNonNull(found, "found"));
        this.status = hasErrors() ? ImportJobStatus.FAILED : ImportJobStatus.REVIEW_REQUIRED;
    }

    /**
     * Publica o job aprovado, materializando o dataset. Falha se a permissão da
     * fonte não autorizar (gate de licença) ou se não estiver em revisão.
     */
    public void publish(PermissionStatus sourcePermission, ReferenceDatasetId datasetId, java.time.Instant when) {
        Objects.requireNonNull(sourcePermission, "sourcePermission");
        Objects.requireNonNull(datasetId, "datasetId");
        Objects.requireNonNull(when, "when");
        if (status != ImportJobStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("só um job em REVIEW_REQUIRED pode ser publicado");
        }
        if (!sourcePermission.allowsPublish()) {
            throw new IllegalStateException("permissão da fonte não autoriza publicação: " + sourcePermission);
        }
        this.status = ImportJobStatus.PUBLISHED;
        this.publishedDatasetId = datasetId;
    }

    /** Falha imediata (ex.: tamanho/MIME) a partir de um estado não terminal. */
    public void fail(ValidationIssue issue) {
        if (status.isTerminal()) {
            throw new IllegalStateException("job já está em estado terminal");
        }
        this.issues.add(Objects.requireNonNull(issue, "issue"));
        this.status = ImportJobStatus.FAILED;
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(ValidationIssue::isError);
    }

    public ImportJobId id() {
        return id;
    }

    public ReferenceSourceId sourceId() {
        return sourceId;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String datasetVersion() {
        return datasetVersion;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public Checksum checksum() {
        return checksum;
    }

    public String rawPayload() {
        return rawPayload;
    }

    public ImportJobStatus status() {
        return status;
    }

    public List<ValidationIssue> issues() {
        return List.copyOf(issues);
    }

    public ReferenceDatasetId publishedDatasetId() {
        return publishedDatasetId;
    }

    public long optimisticVersion() {
        return optimisticVersion;
    }
}
