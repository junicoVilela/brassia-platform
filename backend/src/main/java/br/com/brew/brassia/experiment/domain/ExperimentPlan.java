package br.com.brew.brassia.experiment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Um lote dividido: mesma receita, dois caminhos, uma variável (EXP-001).
 *
 * <p><strong>A hipótese e os fatores são declarados antes de existir resultado, e depois não mudam.</strong>
 * Não há setter para fator nem para hipótese. O motivo não é purismo: um experimento cuja hipótese pode ser
 * reescrita depois de ver o resultado sempre confirma a hipótese — e o registro fica indistinguível de um
 * experimento que realmente previu o efeito.
 *
 * <p>A regra que a história existe para sustentar é a de <strong>uma variável isolada</strong>. Com dois
 * fatores diferentes, todo resultado tem duas explicações e nenhuma pode ser descartada. Por isso o plano
 * não pode nem ser criado nesse estado: {@link ConfoundedExperimentException} no lugar de um aviso.
 */
public final class ExperimentPlan {

    private final UUID id;
    private final UUID breweryId;
    private final UUID recipeId;
    private final String hypothesis;
    private final UUID controlBatchId;
    private final UUID variantBatchId;
    private final List<ExperimentFactor> factors;
    private final Set<String> plannedMeasurements;
    private final boolean sensoryPlanned;
    private final boolean sensoryBlind;
    private final UUID plannedBy;
    private final Instant plannedAt;

    private ExperimentStatus status;
    private Conclusion conclusion;

    private ExperimentPlan(UUID id, UUID breweryId, UUID recipeId, String hypothesis,
            UUID controlBatchId, UUID variantBatchId, List<ExperimentFactor> factors,
            Set<String> plannedMeasurements, boolean sensoryPlanned, boolean sensoryBlind,
            ExperimentStatus status, Conclusion conclusion, UUID plannedBy, Instant plannedAt) {
        this.id = id;
        this.breweryId = breweryId;
        this.recipeId = recipeId;
        this.hypothesis = hypothesis;
        this.controlBatchId = controlBatchId;
        this.variantBatchId = variantBatchId;
        this.factors = List.copyOf(factors);
        this.plannedMeasurements = Set.copyOf(plannedMeasurements);
        this.sensoryPlanned = sensoryPlanned;
        this.sensoryBlind = sensoryBlind;
        this.status = status;
        this.conclusion = conclusion;
        this.plannedBy = plannedBy;
        this.plannedAt = plannedAt;
    }

    /**
     * Planeja o experimento.
     *
     * @throws ConfoundedExperimentException se mais de um fator difere — ou se nenhum difere, porque dois
     *                                       lotes idênticos não testam hipótese nenhuma e o plano ficaria
     *                                       parecendo um experimento à espera de um resultado.
     */
    public static ExperimentPlan plan(UUID id, UUID breweryId, UUID recipeId, String hypothesis,
            UUID controlBatchId, UUID variantBatchId, List<ExperimentFactor> factors,
            Set<String> plannedMeasurements, boolean sensoryPlanned, boolean sensoryBlind,
            UUID plannedBy, Instant plannedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(controlBatchId, "controlBatchId");
        Objects.requireNonNull(variantBatchId, "variantBatchId");
        Objects.requireNonNull(plannedBy, "plannedBy");
        Objects.requireNonNull(plannedAt, "plannedAt");

        var statement = Objects.requireNonNull(hypothesis, "hypothesis").trim();
        if (statement.isEmpty()) {
            throw new IllegalArgumentException("a hipótese não pode ser vazia");
        }
        // Sem hipótese antes, qualquer diferença observada vira "o que a gente queria descobrir".
        if (controlBatchId.equals(variantBatchId)) {
            throw new IllegalArgumentException("controle e variante não podem ser o mesmo lote");
        }

        var differing = factors.stream().filter(ExperimentFactor::differs)
                .map(ExperimentFactor::name).toList();
        if (differing.size() != 1) {
            throw new ConfoundedExperimentException(differing);
        }

        return new ExperimentPlan(id, breweryId, recipeId, statement, controlBatchId, variantBatchId,
                factors, new LinkedHashSet<>(plannedMeasurements), sensoryPlanned, sensoryBlind,
                ExperimentStatus.PLANNED, null, plannedBy, plannedAt);
    }

    /** Reconstrói do banco sem revalidar: o que já foi gravado aconteceu, e a regra vale na entrada. */
    public static ExperimentPlan reconstitute(UUID id, UUID breweryId, UUID recipeId, String hypothesis,
            UUID controlBatchId, UUID variantBatchId, List<ExperimentFactor> factors,
            Set<String> plannedMeasurements, boolean sensoryPlanned, boolean sensoryBlind,
            ExperimentStatus status, Conclusion conclusion, UUID plannedBy, Instant plannedAt) {
        return new ExperimentPlan(id, breweryId, recipeId, hypothesis, controlBatchId, variantBatchId,
                factors, plannedMeasurements, sensoryPlanned, sensoryBlind, status, conclusion,
                plannedBy, plannedAt);
    }

    /** O fator que difere. É sempre exatamente um — a criação garante. */
    public ExperimentFactor isolatedVariable() {
        return factors.stream().filter(ExperimentFactor::differs).findFirst().orElseThrow();
    }

    public void start() {
        if (status != ExperimentStatus.PLANNED) {
            throw new IllegalExperimentTransitionException(status, ExperimentStatus.RUNNING);
        }
        status = ExperimentStatus.RUNNING;
    }

    /**
     * Registra a leitura.
     *
     * <p>As limitações não são parâmetro: saem do plano. Concluir sem elas não é proibido — é
     * inexprimível.
     */
    public void conclude(boolean supported, String observation, UUID concludedBy, Instant at) {
        if (status != ExperimentStatus.RUNNING) {
            throw new IllegalExperimentTransitionException(status, ExperimentStatus.CONCLUDED);
        }
        conclusion = new Conclusion(supported, observation, limitations(), concludedBy, at);
        status = ExperimentStatus.CONCLUDED;
    }

    /**
     * Abandona o experimento.
     *
     * <p>Não se apaga: um experimento abandonado é informação: alguém já tentou isto e parou. Apagar faria
     * a próxima pessoa repetir a mesma tentativa sem saber que ela existiu.
     */
    public void abandon() {
        if (status == ExperimentStatus.CONCLUDED) {
            throw new IllegalExperimentTransitionException(status, ExperimentStatus.ABANDONED);
        }
        status = ExperimentStatus.ABANDONED;
    }

    /**
     * O que este desenho não permite afirmar.
     *
     * <p>Calculado do plano, não digitado. É o que garante que a conclusão nunca saia sem elas.
     */
    public List<Limitation> limitations() {
        var found = EnumSet.noneOf(Limitation.class);
        // Sempre: um par de lotes é n=1, e é a limitação mais esquecida das cinco.
        found.add(Limitation.SINGLE_PAIR);
        if (!sensoryPlanned) {
            found.add(Limitation.NO_SENSORY);
        } else if (!sensoryBlind) {
            found.add(Limitation.SENSORY_NOT_BLIND);
        }
        if (plannedMeasurements.isEmpty()) {
            found.add(Limitation.NO_PLANNED_MEASUREMENT);
        } else if (plannedMeasurements.size() == 1) {
            found.add(Limitation.SINGLE_METRIC);
        }
        return new ArrayList<>(found);
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID recipeId() {
        return recipeId;
    }

    public String hypothesis() {
        return hypothesis;
    }

    public UUID controlBatchId() {
        return controlBatchId;
    }

    public UUID variantBatchId() {
        return variantBatchId;
    }

    public List<ExperimentFactor> factors() {
        return factors;
    }

    public Set<String> plannedMeasurements() {
        return plannedMeasurements;
    }

    public boolean sensoryPlanned() {
        return sensoryPlanned;
    }

    public boolean sensoryBlind() {
        return sensoryBlind;
    }

    public ExperimentStatus status() {
        return status;
    }

    public Optional<Conclusion> conclusion() {
        return Optional.ofNullable(conclusion);
    }

    public UUID plannedBy() {
        return plannedBy;
    }

    public Instant plannedAt() {
        return plannedAt;
    }

    /** Transição que o estado atual não permite. */
    public static final class IllegalExperimentTransitionException extends RuntimeException {

        private final ExperimentStatus current;
        private final ExperimentStatus attempted;

        IllegalExperimentTransitionException(ExperimentStatus current, ExperimentStatus attempted) {
            super("experimento em " + current + " não pode ir para " + attempted);
            this.current = current;
            this.attempted = attempted;
        }

        public ExperimentStatus current() {
            return current;
        }

        public ExperimentStatus attempted() {
            return attempted;
        }
    }
}
