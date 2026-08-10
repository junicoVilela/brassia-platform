package br.com.brew.brassia.experiment.domain;

/** Onde o experimento está (EXP-001). */
public enum ExperimentStatus {
    /** Planejado: hipótese e fatores declarados antes de qualquer resultado existir. */
    PLANNED,
    /** Em andamento: os dois lotes estão sendo produzidos e medidos. */
    RUNNING,
    /** Concluído: há uma leitura registrada, com as limitações que ela carrega. */
    CONCLUDED,
    /** Abandonado. Fica no histórico de propósito — ver ExperimentPlan#abandon. */
    ABANDONED
}
