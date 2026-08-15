package br.com.brew.brassia.packaging.domain;

import java.util.UUID;

/**
 * Lote acabado que não existe nesta cervejaria (SAL-001-B).
 *
 * <p>A mesma exceção para "não existe" e "é de outra cervejaria": distinguir contaria a quem perguntou
 * que o identificador existe em algum lugar.
 */
public class UnknownFinishedLotException extends RuntimeException {

    public UnknownFinishedLotException(UUID id) {
        super("o lote acabado " + id + " não foi encontrado");
    }
}
