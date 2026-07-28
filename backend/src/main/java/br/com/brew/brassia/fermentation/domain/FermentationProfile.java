package br.com.brew.brassia.fermentation.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil de fermentação versionado (FER-001). Nasce em DRAFT (editável); publicar congela
 * a versão (imutável) — o histórico não é reescrito. Editar um perfil publicado gera uma
 * nova versão (orquestrado no caso de uso). Estágios ordenados por sequência única.
 */
public final class FermentationProfile {

    private final ProfileId id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private final int version;
    private final ProfileStatus status;
    private List<FermentationStage> stages;

    private FermentationProfile(ProfileId id, UUID breweryId, String code, String name, int version,
            ProfileStatus status, List<FermentationStage> stages) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 160);
        if (version <= 0) {
            throw new IllegalArgumentException("versão deve ser positiva");
        }
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.stages = validateStages(stages);
    }

    public static FermentationProfile draft(UUID breweryId, String code, String name, int version,
            List<FermentationStage> stages) {
        return new FermentationProfile(ProfileId.newId(), breweryId, code, name, version, ProfileStatus.DRAFT,
                stages);
    }

    public static FermentationProfile reconstitute(ProfileId id, UUID breweryId, String code, String name,
            int version, ProfileStatus status, List<FermentationStage> stages) {
        return new FermentationProfile(id, breweryId, code, name, version, status, stages);
    }

    public boolean draftStatus() {
        return status == ProfileStatus.DRAFT;
    }

    /** Atualiza o rascunho (nome + estágios); um perfil publicado é imutável. */
    public void update(String name, List<FermentationStage> stages) {
        if (status != ProfileStatus.DRAFT) {
            throw new IllegalStateException("perfil publicado é imutável; crie uma nova versão");
        }
        this.name = requireText(name, "nome", 160);
        this.stages = validateStages(stages);
    }

    private static List<FermentationStage> validateStages(List<FermentationStage> stages) {
        Objects.requireNonNull(stages, "stages");
        var sequences = stages.stream().map(FermentationStage::sequence).distinct().count();
        if (sequences != stages.size()) {
            throw new IllegalArgumentException("sequências dos estágios devem ser únicas");
        }
        return List.copyOf(stages);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }

    public ProfileId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public String name() { return name; }
    public int version() { return version; }
    public ProfileStatus status() { return status; }
    public List<FermentationStage> stages() { return stages; }
}
