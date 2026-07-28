package br.com.brew.brassia.sanitation.domain;

/**
 * Estados de um ciclo de limpeza/sanitização (CLN-003). A interrupção é preservada
 * (INTERRUPTED é retomável); COMPLETED encerra a execução — a verificação e liberação
 * ficam para CLN-004.
 */
public enum CleaningCycleStatus {
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    RELEASED,
    REJECTED
}
