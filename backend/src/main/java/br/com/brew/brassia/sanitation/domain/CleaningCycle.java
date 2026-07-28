package br.com.brew.brassia.sanitation.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de limpeza/sanitização (CLN-003). Referencia uma versão publicada de POP e um
 * equipamento; ao iniciar, congela um snapshot das etapas/faixas (imutável mesmo que
 * outra versão seja publicada depois). State machine:
 * IN_PROGRESS → INTERRUPTED → IN_PROGRESS → COMPLETED. Etapa fora de ordem exige motivo;
 * a interrupção é preservada. Parâmetro fora da ficha é bloqueado (salvo override).
 */
public final class CleaningCycle {

    private final UUID id;
    private final UUID breweryId;
    private final UUID procedureId;
    private final String procedureCode;
    private final int procedureVersion;
    private final UUID equipmentId;
    private final List<CycleStep> steps;
    private final Instant startedAt;

    private CleaningCycleStatus status;
    private String interruptReason;
    private Instant endedAt;

    private CleaningCycle(UUID id, UUID breweryId, UUID procedureId, String procedureCode, int procedureVersion,
            UUID equipmentId, List<CycleStep> steps, CleaningCycleStatus status, String interruptReason,
            Instant startedAt, Instant endedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.procedureId = Objects.requireNonNull(procedureId, "procedureId");
        this.procedureCode = Objects.requireNonNull(procedureCode, "procedureCode");
        this.procedureVersion = procedureVersion;
        this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("ciclo precisa de ao menos uma etapa");
        }
        this.steps = steps.stream().sorted(Comparator.comparingInt(CycleStep::sequence)).toList();
        this.status = Objects.requireNonNull(status, "status");
        this.interruptReason = interruptReason;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = endedAt;
    }

    /** Inicia um ciclo IN_PROGRESS congelando as etapas da versão publicada do POP. */
    public static CleaningCycle start(UUID breweryId, CleaningProcedure procedure, UUID equipmentId) {
        if (procedure.status() != ProcedureStatus.PUBLISHED) {
            throw new IllegalArgumentException("o ciclo exige um POP publicado");
        }
        var snapshot = procedure.steps().stream().map(CycleStep::snapshot).toList();
        return new CleaningCycle(UUID.randomUUID(), breweryId, procedure.id().value(), procedure.code(),
                procedure.version(), Objects.requireNonNull(equipmentId, "equipmentId"), snapshot,
                CleaningCycleStatus.IN_PROGRESS, null, Instant.now(), null);
    }

    public static CleaningCycle reconstitute(UUID id, UUID breweryId, UUID procedureId, String procedureCode,
            int procedureVersion, UUID equipmentId, List<CycleStep> steps, CleaningCycleStatus status,
            String interruptReason, Instant startedAt, Instant endedAt) {
        return new CleaningCycle(id, breweryId, procedureId, procedureCode, procedureVersion, equipmentId, steps,
                status, interruptReason, startedAt, endedAt);
    }

    /** Registra a execução de uma etapa; fora de ordem exige motivo. */
    public void recordStep(int sequence, StepExecution exec) {
        requireInProgress();
        var step = steps.stream().filter(s -> s.sequence() == sequence).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("etapa inexistente: " + sequence));
        var nextPending = steps.stream().filter(CycleStep::pending)
                .map(CycleStep::sequence).min(Integer::compareTo).orElse(sequence);
        if (sequence != nextPending && isBlank(exec.outOfOrderReason())) {
            throw new IllegalArgumentException("etapa fora de ordem exige motivo (próxima pendente: " + nextPending + ")");
        }
        step.execute(exec);
    }

    /** Interrompe o ciclo preservando o estado; retomável. */
    public void interrupt(String reason) {
        requireInProgress();
        if (isBlank(reason)) {
            throw new IllegalArgumentException("interrupção exige motivo");
        }
        this.interruptReason = reason.trim();
        this.status = CleaningCycleStatus.INTERRUPTED;
    }

    /** Retoma um ciclo interrompido. */
    public void resume() {
        if (status != CleaningCycleStatus.INTERRUPTED) {
            throw new IllegalStateException("só é possível retomar um ciclo interrompido");
        }
        this.status = CleaningCycleStatus.IN_PROGRESS;
    }

    /** Encerra a execução; exige todas as etapas concluídas. */
    public void complete() {
        requireInProgress();
        if (steps.stream().anyMatch(CycleStep::pending)) {
            throw new IllegalStateException("há etapas pendentes; conclua todas antes de encerrar");
        }
        this.status = CleaningCycleStatus.COMPLETED;
        this.endedAt = Instant.now();
    }

    private void requireInProgress() {
        if (status != CleaningCycleStatus.IN_PROGRESS) {
            throw new IllegalStateException("ciclo não está em andamento (estado: " + status + ")");
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID procedureId() { return procedureId; }
    public String procedureCode() { return procedureCode; }
    public int procedureVersion() { return procedureVersion; }
    public UUID equipmentId() { return equipmentId; }
    public List<CycleStep> steps() { return steps; }
    public CleaningCycleStatus status() { return status; }
    public String interruptReason() { return interruptReason; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
}
