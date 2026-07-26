package br.com.brew.brassia.planning.domain;

/**
 * Conflito de equipamento: a janela solicitada sobrepõe outra entrada planejada
 * no mesmo equipamento. Estende {@link IllegalStateException} para ser mapeada a
 * HTTP 409 pelo tratador global, tanto no pré-check do caso de uso quanto no
 * backstop de concorrência (exclusion constraint) do adaptador de persistência.
 */
public class ScheduleConflictException extends IllegalStateException {

    public ScheduleConflictException(String message) {
        super(message);
    }

    public ScheduleConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
