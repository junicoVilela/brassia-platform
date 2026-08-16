package br.com.brew.brassia.community.domain;

/**
 * A sugestão já tinha sido decidida (COM-004).
 *
 * <p>Decidir duas vezes reescreveria quem decidiu e quando — e é justamente esse registro que torna a
 * conversa um histórico auditável em vez de uma caixa de entrada.
 */
public class AlreadyDecidedException extends RuntimeException {

    public AlreadyDecidedException(ContributionStatus status) {
        super("esta sugestão já foi " + (status == ContributionStatus.ACCEPTED ? "aceita" : "recusada"));
    }
}
