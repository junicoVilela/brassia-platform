package br.com.brew.brassia.planning.domain;

/**
 * Estado de uma entrada da agenda de produção. Em PLN-001 a entrada nasce e
 * permanece {@code PLANNED}; transições (ex.: consumo por uma ordem) entram nas
 * histórias seguintes (BOP-*).
 */
public enum ScheduleStatus {
    PLANNED
}
