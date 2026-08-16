package br.com.brew.brassia.container.domain;

/** O ciclo do contêiner é máquina de estados, e não campo livre. */
public class IllegalContainerTransitionException extends RuntimeException {

    public IllegalContainerTransitionException(ContainerState from, ContainerState to) {
        super("Um contêiner em " + from + " não vai para " + to + ".");
    }
}
