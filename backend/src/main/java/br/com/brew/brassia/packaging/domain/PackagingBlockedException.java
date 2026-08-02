package br.com.brew.brassia.packaging.domain;

import java.util.List;
import java.util.Objects;

/**
 * A reserva do plano de envase foi recusada: a lista completa de bloqueios acompanha o erro,
 * para o operador resolver tudo de uma vez em vez de descobrir um impedimento por tentativa.
 */
public final class PackagingBlockedException extends RuntimeException {

    private final transient List<Blocker> blockers;

    public PackagingBlockedException(List<Blocker> blockers) {
        super("envase bloqueado");
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
