package br.com.brew.brassia.integration.domain;

/**
 * Código ilegível ou desconhecido (INT-003).
 *
 * <p>A mensagem que chega ao cliente é sempre a mesma, independentemente do motivo. Distinguir "formato
 * inválido" de "tipo que não existe" ensinaria a quem estivesse sondando quais tipos o sistema conhece — e
 * um QR é lido por quem passa perto dele.
 */
public class UnknownScanCodeException extends RuntimeException {

    public UnknownScanCodeException(String reason) {
        super(reason);
    }
}
