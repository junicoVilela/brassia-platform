package br.com.brew.brassia.sales.domain;

import java.util.UUID;

/**
 * Produto ou canal que não existe nesta cervejaria (SAL-001).
 *
 * <p>A mesma exceção para "não existe" e "é de outra cervejaria", pelo mesmo motivo da CRM-001:
 * distinguir contaria a quem perguntou que o identificador existe em algum lugar.
 */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String what, UUID id) {
        super(what + " " + id + " não foi encontrado");
    }
}
