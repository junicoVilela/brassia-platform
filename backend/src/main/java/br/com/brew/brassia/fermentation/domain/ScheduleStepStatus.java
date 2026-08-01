package br.com.brew.brassia.fermentation.domain;

/**
 * Estado de uma etapa da agenda (FER-004). Executada é terminal para efeito de replanejamento:
 * o passado não se move.
 */
public enum ScheduleStepStatus {
    PLANNED,
    DONE;

    public boolean done() {
        return this == DONE;
    }
}
