package br.com.brew.brassia.quality.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Plano de controle (QLT-001): que parâmetros se mede numa etapa, em que faixa, com que frequência
 * e o que fazer quando saem dela.
 *
 * <p><strong>Versionado e publicado</strong>, no mesmo padrão do perfil de fermentação (FER-001) e
 * do modelo de rótulo (PKG-004). A medição grava contra qual versão foi julgada, então apertar um
 * limite hoje não transforma em desvio uma medição que estava conforme ontem.
 *
 * @param recipeId receita a que o plano se aplica; ausente significa "vale para todas", que é o
 *                 caso dos controles de casa (higiene, água) que não dependem do produto
 */
public final class ControlPlan {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private String name;
    private UUID recipeId;
    private ProcessStage stage;
    private ControlPlanStatus status;
    private final int version;
    private final List<ControlPoint> points;
    private final long lockVersion;

    private ControlPlan(UUID id, UUID breweryId, String code, String name, UUID recipeId, ProcessStage stage,
            ControlPlanStatus status, int version, List<ControlPoint> points, long lockVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = requireText(code, "código", 40);
        this.name = requireText(name, "nome", 120);
        this.recipeId = recipeId;
        this.stage = Objects.requireNonNull(stage, "etapa é obrigatória");
        this.status = Objects.requireNonNull(status, "situação");
        this.version = version;
        this.points = new ArrayList<>(Objects.requireNonNull(points, "pontos"));
        this.lockVersion = lockVersion;
    }

    public static ControlPlan draft(UUID breweryId, String code, String name, UUID recipeId,
            ProcessStage stage) {
        return new ControlPlan(UUID.randomUUID(), breweryId, code, name, recipeId, stage,
                ControlPlanStatus.DRAFT, 1, List.of(), 0);
    }

    public static ControlPlan reconstitute(UUID id, UUID breweryId, String code, String name, UUID recipeId,
            ProcessStage stage, ControlPlanStatus status, int version, List<ControlPoint> points,
            long lockVersion) {
        return new ControlPlan(id, breweryId, code, name, recipeId, stage, status, version, points,
                lockVersion);
    }

    /**
     * Nova versão a partir de um plano publicado; a anterior fica intacta como histórico.
     *
     * <p>Os pontos são <strong>copiados com identidade nova</strong>. Reaproveitar os ids faria
     * duas versões apontarem para o mesmo ponto, e uma medição antiga passaria a referenciar o
     * limite da versão nova — exatamente o que o versionamento existe para impedir.
     */
    public ControlPlan newDraftVersion() {
        requirePublished();
        var copies = points.stream()
                .map(p -> ControlPoint.of(p.parameter(), p.limits(), p.frequency(), p.action(),
                        p.severity(), p.critical()))
                .toList();
        return new ControlPlan(UUID.randomUUID(), breweryId, code, name, recipeId, stage,
                ControlPlanStatus.DRAFT, version + 1, copies, 0);
    }

    public void addPoint(ControlPoint point) {
        requireDraft();
        Objects.requireNonNull(point, "ponto");
        if (points.stream().anyMatch(p -> p.parameter().equalsIgnoreCase(point.parameter()))) {
            throw new IllegalArgumentException("o plano já controla o parâmetro " + point.parameter());
        }
        points.add(point);
    }

    public void removePoint(UUID pointId) {
        requireDraft();
        if (!points.removeIf(p -> p.id().equals(pointId))) {
            throw new IllegalArgumentException("ponto inexistente no plano");
        }
    }

    public void amend(String name, UUID recipeId, ProcessStage stage) {
        requireDraft();
        this.name = requireText(name, "nome", 120);
        this.recipeId = recipeId;
        this.stage = Objects.requireNonNull(stage, "etapa é obrigatória");
    }

    /** Publicar congela a versão. Plano sem ponto não controla nada e por isso não publica. */
    public void publish() {
        requireDraft();
        if (points.isEmpty()) {
            throw new IllegalStateException("plano sem ponto de controle não publica");
        }
        this.status = ControlPlanStatus.PUBLISHED;
    }

    /**
     * Julga um valor contra um ponto. Só plano publicado julga — rascunho pode ter limite pela
     * metade, e o veredito mudaria sozinho quando alguém salvasse a edição.
     */
    public Optional<SpecLimits.Violation> judge(UUID pointId, java.math.BigDecimal value) {
        if (!status.judges()) {
            throw new PlanNotPublishedException(code);
        }
        return point(pointId)
                .orElseThrow(() -> new IllegalArgumentException("ponto inexistente no plano"))
                .limits()
                .violation(value);
    }

    public Optional<ControlPoint> point(UUID pointId) {
        return points.stream().filter(p -> p.id().equals(pointId)).findFirst();
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Optional<UUID> recipeId() {
        return Optional.ofNullable(recipeId);
    }

    public ProcessStage stage() {
        return stage;
    }

    public ControlPlanStatus status() {
        return status;
    }

    public int version() {
        return version;
    }

    public List<ControlPoint> points() {
        return List.copyOf(points);
    }

    public long lockVersion() {
        return lockVersion;
    }

    private void requireDraft() {
        if (status != ControlPlanStatus.DRAFT) {
            throw new IllegalStateException("plano publicado é imutável; crie uma nova versão");
        }
    }

    private void requirePublished() {
        if (status != ControlPlanStatus.PUBLISHED) {
            throw new IllegalStateException("só plano publicado gera nova versão");
        }
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
}
