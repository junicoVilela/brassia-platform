package br.com.brew.brassia.fieldfeedback.domain;

import java.util.List;

/**
 * A reclamação exige ações que ainda não foram atendidas nem dispensadas (FLD-001).
 *
 * <p><strong>Recusar o encerramento é o que dá dentes ao critério.</strong> Um sistema que apenas
 * <em>sugere</em> quarentena depende de alguém concordar no dia em que está com pressa — e o dia em que se
 * está com pressa é exatamente o dia em que uma reclamação de corpo estranho é encerrada como "cliente
 * contatado, caso resolvido".
 *
 * <p>A dispensa existe e é legítima: às vezes o corpo estranho era do copo do consumidor. Mas ela é
 * explícita, assinada e justificada — não o caminho de menor resistência.
 */
public final class PendingActionsException extends RuntimeException {

    private final List<RequiredAction> pending;

    PendingActionsException(List<RequiredAction> pending) {
        super("ações exigidas ainda pendentes: " + pending);
        this.pending = List.copyOf(pending);
    }

    public List<RequiredAction> pending() {
        return pending;
    }
}
