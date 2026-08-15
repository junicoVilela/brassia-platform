package br.com.brew.brassia.crm.domain;

/**
 * Já existe cliente com este documento na cervejaria (CRM-001).
 *
 * <p>A garantia real é o índice parcial {@code ux_crm_customer_tax_id} — checagem prévia não é garantia,
 * porque duas requisições simultâneas passam as duas por ela. Esta exceção existe para que o caso comum
 * devolva 409 com o nome do cliente que já usa o documento, em vez de um erro de banco que não diz
 * quem é. O índice continua sendo quem impede de verdade.
 */
public class DuplicateTaxIdException extends RuntimeException {

    private final String taxId;

    public DuplicateTaxIdException(String taxId, String existingName) {
        super("o documento " + taxId + " já está cadastrado para " + existingName);
        this.taxId = taxId;
    }

    public String taxId() {
        return taxId;
    }
}
