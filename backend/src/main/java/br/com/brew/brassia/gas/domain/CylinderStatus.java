package br.com.brew.brassia.gas.domain;

/**
 * Situação do cilindro (GAS-001). {@code BLOCKED} é decisão humana com motivo (avaria, suspeita de
 * contaminação, requalificação vencida) e só sai por desbloqueio explícito.
 */
public enum CylinderStatus {
    AVAILABLE,
    CONNECTED,
    EMPTY,
    BLOCKED
}
