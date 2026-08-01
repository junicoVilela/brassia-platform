package br.com.brew.brassia.fermentation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha do tempo de fermentação de um lote (FER-004). Nasce de um perfil publicado — o que
 * também dá ao lote o perfil que rege a estabilidade de FG — e admite etapas específicas do
 * lote (dry hop, cold crash, transferência).
 *
 * <p>Replanejar uma data propaga <strong>apenas</strong> pela cadeia de etapas encadeadas e
 * ainda pendentes: etapa executada e etapa-âncora (com data própria) não se movem. E a
 * propagação é sempre calculada como prévia antes de ser aplicada — o cervejeiro vê o que vai
 * mudar antes de mudar.
 */
public final class FermentationSchedule {

    private final UUID id;
    private final UUID breweryId;
    private final UUID batchId;
    private final UUID profileId;
    private final int profileVersion;
    private final List<ScheduleStep> steps;

    private FermentationSchedule(UUID id, UUID breweryId, UUID batchId, UUID profileId, int profileVersion,
            List<ScheduleStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.profileId = Objects.requireNonNull(profileId, "perfil é obrigatório");
        this.profileVersion = profileVersion;
        this.steps = new ArrayList<>(validate(steps));
    }

    public static FermentationSchedule create(UUID breweryId, UUID batchId, UUID profileId, int profileVersion,
            List<ScheduleStep> steps) {
        return new FermentationSchedule(UUID.randomUUID(), breweryId, batchId, profileId, profileVersion, steps);
    }

    public static FermentationSchedule reconstitute(UUID id, UUID breweryId, UUID batchId, UUID profileId,
            int profileVersion, List<ScheduleStep> steps) {
        return new FermentationSchedule(id, breweryId, batchId, profileId, profileVersion, steps);
    }

    /**
     * Deriva as etapas de um perfil publicado: cada estágio vira uma janela sequencial a
     * partir de {@code start}. Estágio por tempo dura os dias declarados; os demais recebem a
     * duração padrão, porque o fim depende de leitura ou de decisão humana.
     */
    public static List<ScheduleStep> fromProfile(FermentationProfile profile, Instant start, UUID responsibleUserId,
            int defaultDurationDays, int toleranceHours) {
        if (defaultDurationDays <= 0) {
            throw new IllegalArgumentException("duração padrão deve ser positiva");
        }
        var steps = new ArrayList<ScheduleStep>();
        var cursor = start;
        for (var stage : profile.stages().stream()
                .sorted(Comparator.comparingInt(FermentationStage::sequence)).toList()) {
            var days = stage.condition() == AdvanceCondition.TIME ? stage.conditionDays() : defaultDurationDays;
            var end = cursor.plus(Duration.ofDays(days));
            steps.add(ScheduleStep.plan(steps.size() + 1, stage.name(), ScheduleAction.REST, stage.condition(),
                    stage.conditionDays(), stage.targetGravity(), cursor, end, toleranceHours, responsibleUserId,
                    // A primeira ancora no início da fermentação; as seguintes seguem a anterior.
                    !steps.isEmpty()));
            cursor = end;
        }
        return steps;
    }

    /** Acrescenta uma etapa específica do lote, mantendo a sequência contígua. */
    public ScheduleStep addStep(String name, ScheduleAction action, AdvanceCondition condition, Integer conditionDays,
            java.math.BigDecimal targetGravity, Instant plannedStart, Instant plannedEnd, int toleranceHours,
            UUID responsibleUserId, boolean dependsOnPrevious) {
        var step = ScheduleStep.plan(steps.size() + 1, name, action, condition, conditionDays, targetGravity,
                plannedStart, plannedEnd, toleranceHours, responsibleUserId, dependsOnPrevious);
        steps.add(step);
        return step;
    }

    /**
     * Calcula o efeito de mover o início de uma etapa, sem aplicar nada. A cadeia para na
     * primeira etapa que não depende da anterior ou que já foi executada.
     */
    public ReschedulePreview previewMove(UUID stepId, Instant newStart) {
        var ordered = ordered();
        var index = indexOf(ordered, stepId);
        var target = ordered.get(index);
        if (target.status().done()) {
            throw new IllegalStateException("etapa executada não é replanejada");
        }
        Objects.requireNonNull(newStart, "novo início é obrigatório");

        var delta = Duration.between(target.plannedStart(), newStart);
        var changes = new ArrayList<ReschedulePreview.Change>();
        changes.add(ReschedulePreview.Change.of(target, delta));

        var blocked = new ArrayList<ReschedulePreview.Blocked>();
        for (int i = index + 1; i < ordered.size(); i++) {
            var step = ordered.get(i);
            if (!step.dependsOnPrevious()) {
                blocked.add(new ReschedulePreview.Blocked(step, "etapa com data própria (âncora)"));
                break;
            }
            if (step.status().done()) {
                blocked.add(new ReschedulePreview.Blocked(step, "etapa já executada"));
                break;
            }
            changes.add(ReschedulePreview.Change.of(step, delta));
        }
        return new ReschedulePreview(delta, changes, blocked);
    }

    /** Aplica exatamente o que a prévia mostrou. */
    public ReschedulePreview move(UUID stepId, Instant newStart) {
        var preview = previewMove(stepId, newStart);
        for (var change : preview.changes()) {
            step(change.stepId()).shift(preview.delta());
        }
        return preview;
    }

    public void execute(UUID stepId, Instant at, String justification) {
        step(stepId).execute(at, justification);
    }

    /** Etapas pendentes cuja janela venceu além da tolerância. */
    public List<ScheduleStep> lateSteps(Instant now) {
        return ordered().stream().filter(s -> s.lateAt(now)).toList();
    }

    public ScheduleStep step(UUID stepId) {
        return steps.stream().filter(s -> s.id().equals(stepId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("etapa inexistente: " + stepId));
    }

    public List<ScheduleStep> ordered() {
        return steps.stream().sorted(Comparator.comparingInt(ScheduleStep::sequence)).toList();
    }

    private static int indexOf(List<ScheduleStep> ordered, UUID stepId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(stepId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("etapa inexistente: " + stepId);
    }

    private static List<ScheduleStep> validate(List<ScheduleStep> steps) {
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("agenda exige ao menos uma etapa");
        }
        var sequences = steps.stream().map(ScheduleStep::sequence).distinct().count();
        if (sequences != steps.size()) {
            throw new IllegalArgumentException("sequências das etapas devem ser únicas");
        }
        return steps;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID batchId() { return batchId; }
    public UUID profileId() { return profileId; }
    public int profileVersion() { return profileVersion; }
    public List<ScheduleStep> steps() { return List.copyOf(steps); }
}
