package br.com.brew.brassia.crm.domain;

import java.util.UUID;

/**
 * Cliente ou contato que não existe nesta cervejaria (CRM-001).
 *
 * <p><strong>A mesma exceção para "não existe" e "é de outra cervejaria", de propósito.</strong>
 * Distinguir os dois casos na resposta contaria a quem perguntou que o identificador existe em algum
 * lugar — um oráculo de existência que atravessa a fronteira entre cervejarias. As buscas do repositório
 * já filtram por cervejaria, então quem está fora simplesmente não acha.
 */
public class UnknownCustomerException extends RuntimeException {

    public UnknownCustomerException(String what, UUID id) {
        super(what + " " + id + " não foi encontrado");
    }
}
