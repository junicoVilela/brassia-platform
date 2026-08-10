package br.com.brew.brassia.fieldfeedback.domain;

/**
 * O lote da reclamação não existe nesta cervejaria (FLD-001).
 *
 * <p>Uma reclamação presa a um lote que não existe não entra em nenhuma análise por lote — some do
 * relatório que deveria encontrá-la.
 */
public final class UnknownComplaintBatchException extends RuntimeException {

    public UnknownComplaintBatchException(String message) {
        super(message);
    }
}
