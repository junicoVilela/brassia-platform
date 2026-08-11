package br.com.brew.brassia.equipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado de limpeza do equipamento (CLN-004-A).
 *
 * <p><strong>O que suja é receber cerveja; o que limpa é um ciclo liberado.</strong> Não há caminho para
 * marcar limpo à mão: se existisse, ele seria o caminho usado no dia de correria, e o estado deixaria de
 * significar "há evidência de sanitização" para significar "alguém clicou". A evidência é o ciclo, com
 * suas medições de concentração, temperatura e ATP.
 *
 * <p><strong>Nunca usado é limpo.</strong> Exigir ciclo antes do primeiro uso obrigaria a registrar a
 * limpeza de um tanque recém-chegado — e a primeira coisa que se aprende com uma regra assim é burlá-la.
 */
public record Cleanliness(State state, Instant soiledAt, Instant cleanedAt, UUID cleanedByCycleId) {

    public enum State {
        CLEAN,
        DIRTY
    }

    public Cleanliness {
        Objects.requireNonNull(state, "state");
        if (state == State.DIRTY && soiledAt == null) {
            // "Está sujo" não é acionável sozinho: quem programa a limpeza precisa saber se o tanque
            // esvaziou hoje de manhã ou há três semanas.
            throw new IllegalArgumentException("equipamento sujo precisa registrar quando sujou");
        }
        if ((cleanedAt == null) != (cleanedByCycleId == null)) {
            // Limpo sem ciclo seria uma palavra numa coluna: quem audita não chegaria às medições.
            throw new IllegalArgumentException("limpeza registrada precisa do ciclo que a sustenta");
        }
    }

    /** Equipamento recém-cadastrado: nunca sujou, ninguém precisou limpar. */
    public static Cleanliness neverUsed() {
        return new Cleanliness(State.CLEAN, null, null, null);
    }

    /**
     * Recebeu cerveja.
     *
     * <p>Sujar de novo o que já está sujo não move a data: o que importa é <em>desde quando</em> está
     * sujo, e reiniciar a contagem esconderia o tanque parado há três semanas atrás de um uso recente.
     */
    public Cleanliness soil(Instant at) {
        Objects.requireNonNull(at, "at");
        if (state == State.DIRTY) {
            return this;
        }
        return new Cleanliness(State.DIRTY, at, cleanedAt, cleanedByCycleId);
    }

    /** Um ciclo de limpeza foi liberado sobre este equipamento. */
    public Cleanliness cleanedBy(UUID cycleId, Instant at) {
        Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(at, "at");
        return new Cleanliness(State.CLEAN, null, at, cycleId);
    }

    public boolean isClean() {
        return state == State.CLEAN;
    }

    public Optional<Instant> soiledSince() {
        return Optional.ofNullable(soiledAt);
    }
}
