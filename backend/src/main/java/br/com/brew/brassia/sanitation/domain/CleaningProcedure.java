package br.com.brew.brassia.sanitation.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * POP de limpeza/sanitização versionado (CLN-001). Nasce em DRAFT (editável);
 * publicar congela a versão (imutável) — o ciclo referencia a versão publicada.
 * Editar um POP publicado gera uma nova versão (orquestrado no caso de uso).
 */
public final class CleaningProcedure {

    private final ProcedureId id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private final int version;
    private final ProcedureStatus status;
    private List<ProcedureStep> steps;

    private CleaningProcedure(ProcedureId id, UUID breweryId, String code, String name, int version,
            ProcedureStatus status, List<ProcedureStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 160);
        if (version <= 0) {
            throw new IllegalArgumentException("versão deve ser positiva");
        }
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
        this.steps = validateSteps(steps);
    }

    public static CleaningProcedure draft(UUID breweryId, String code, String name, int version,
            List<ProcedureStep> steps) {
        return new CleaningProcedure(ProcedureId.newId(), breweryId, code, name, version, ProcedureStatus.DRAFT,
                steps);
    }

    public static CleaningProcedure reconstitute(ProcedureId id, UUID breweryId, String code, String name,
            int version, ProcedureStatus status, List<ProcedureStep> steps) {
        return new CleaningProcedure(id, breweryId, code, name, version, status, steps);
    }

    public boolean draftStatus() {
        return status == ProcedureStatus.DRAFT;
    }

    /** Atualiza o rascunho (nome + etapas); um POP publicado é imutável. */
    public void update(String name, List<ProcedureStep> steps) {
        if (status != ProcedureStatus.DRAFT) {
            throw new IllegalStateException("POP publicado é imutável; crie uma nova versão");
        }
        this.name = requireText(name, "nome", 160);
        this.steps = validateSteps(steps);
    }

    private static List<ProcedureStep> validateSteps(List<ProcedureStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var sequences = steps.stream().map(ProcedureStep::sequence).distinct().count();
        if (sequences != steps.size()) {
            throw new IllegalArgumentException("sequências das etapas devem ser únicas");
        }
        return List.copyOf(steps);
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

    public ProcedureId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public String name() { return name; }
    public int version() { return version; }
    public ProcedureStatus status() { return status; }
    public List<ProcedureStep> steps() { return steps; }
}
