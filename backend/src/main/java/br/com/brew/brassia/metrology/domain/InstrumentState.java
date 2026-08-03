package br.com.brew.brassia.metrology.domain;

/**
 * Situação cadastral do instrumento — decisão humana, não consequência do tempo.
 *
 * <p>Vencimento de calibração <em>não</em> é estado: é derivado da data e aparece em
 * {@link Fitness}. Misturar os dois faria um instrumento "desbloquear" sozinho ao ser recalibrado
 * e esconderia que alguém o tirou de circulação de propósito.
 */
public enum InstrumentState {
    ACTIVE,
    BLOCKED,
    RETIRED;

    public boolean terminal() {
        return this == RETIRED;
    }
}
