package br.com.brew.brassia.community.domain;

/**
 * A denúncia já tinha sido revisada (COM-005).
 *
 * <p>Rever a revisão reescreveria quem decidiu e quando — e é esse registro que torna a moderação
 * auditável, que é literalmente o que o critério da história pede.
 */
public class AlreadyReviewedException extends RuntimeException {

    public AlreadyReviewedException(ReportOutcome outcome) {
        super("esta denúncia já foi revisada e "
                + (outcome == ReportOutcome.UPHELD ? "julgada procedente" : "julgada improcedente"));
    }
}
