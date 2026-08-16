package br.com.brew.brassia.distribution.domain;

/** O ciclo da carga é máquina de estados, e não campo livre. */
public class IllegalLoadTransitionException extends RuntimeException {

    public IllegalLoadTransitionException(LoadStatus from, LoadStatus to) {
        super("Uma carga em " + from + " não vai para " + to + ".");
    }

    public static IllegalLoadTransitionException frozen(LoadStatus status) {
        return new IllegalLoadTransitionException(status, LoadStatus.PLANNED);
    }
}
