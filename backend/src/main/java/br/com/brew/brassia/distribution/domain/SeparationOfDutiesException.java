package br.com.brew.brassia.distribution.domain;

/**
 * Quem montou a carga não é quem a libera.
 *
 * <p>Não é desconfiança do motorista: é que a conferência serve para <strong>encontrar o erro de quem
 * montou</strong>, e quem montou relê o próprio trabalho enxergando o que quis colocar, e não o que
 * colocou. Uma conferência feita pela mesma pessoa custa o mesmo tempo e não encontra nada.
 */
public class SeparationOfDutiesException extends RuntimeException {

    public SeparationOfDutiesException() {
        super("Quem montou a carga não pode liberá-la. A conferência existe para encontrar o erro de "
                + "quem montou.");
    }
}
