package br.com.brew.brassia.planning.domain;

import java.util.List;

/**
 * A liberação da OP falhou porque há bloqueios (BOP-002 — "falha lista bloqueios").
 * Carrega a lista completa para a resposta 409.
 */
public class ReleaseBlockedException extends RuntimeException {

    private final transient List<ReleaseBlocker> blockers;

    public ReleaseBlockedException(List<ReleaseBlocker> blockers) {
        super("liberação bloqueada");
        this.blockers = List.copyOf(blockers);
    }

    public List<ReleaseBlocker> blockers() {
        return blockers;
    }
}
