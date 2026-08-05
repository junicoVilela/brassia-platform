package br.com.brew.brassia.traceability.domain;

/**
 * Tentativa de encerrar um recall com destino ainda não comunicado (FDS-003).
 *
 * <p>Encerrar assim declararia terminada uma operação que deixou cerveja na prateleira de quem não
 * foi avisado — e o dossiê passaria a dizer isso para sempre.
 */
public final class PendingNotificationsException extends RuntimeException {

    private final int pending;

    public PendingNotificationsException(int pending) {
        super("há destinos sem comunicação registrada");
        this.pending = pending;
    }

    public int pending() {
        return pending;
    }
}
