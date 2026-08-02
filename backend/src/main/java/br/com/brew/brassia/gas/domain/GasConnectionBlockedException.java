package br.com.brew.brassia.gas.domain;

import java.util.List;
import java.util.Objects;

/**
 * A conexão de gás foi recusada. A lista completa de impedimentos acompanha o erro — em rede de
 * gás, descobrir um problema por tentativa significa montar e desmontar a linha várias vezes.
 */
public final class GasConnectionBlockedException extends RuntimeException {

    private final transient List<Blocker> blockers;

    public GasConnectionBlockedException(List<Blocker> blockers) {
        super("conexão de gás bloqueada");
        this.blockers = List.copyOf(Objects.requireNonNull(blockers));
        if (this.blockers.isEmpty()) {
            throw new IllegalArgumentException("bloqueio sem motivo não é rastreável");
        }
    }

    public List<Blocker> blockers() {
        return blockers;
    }

    /** Motivo estável ({@code code}) mais frase segura para a interface. */
    public record Blocker(String code, String message) {
        public Blocker {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
