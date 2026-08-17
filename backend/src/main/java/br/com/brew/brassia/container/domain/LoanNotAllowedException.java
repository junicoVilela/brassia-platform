package br.com.brew.brassia.container.domain;

/** O empréstimo não pode ser registrado agora — e a mensagem diz por quê. */
public class LoanNotAllowedException extends RuntimeException {

    private final String reasonCode;

    private LoanNotAllowedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static LoanNotAllowedException alreadyLent(String code) {
        return new LoanNotAllowedException("already_lent",
                "O vasilhame " + code + " já está emprestado. O mesmo keg com dois clientes ao mesmo "
                        + "tempo contabilizaria duas cauções.");
    }

    public static LoanNotAllowedException noOpenLoan() {
        return new LoanNotAllowedException("no_open_loan",
                "Não há empréstimo aberto para este vasilhame.");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
