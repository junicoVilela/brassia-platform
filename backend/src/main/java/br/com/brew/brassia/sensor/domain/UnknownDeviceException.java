package br.com.brew.brassia.sensor.domain;

/**
 * Dispositivo não cadastrado nesta cervejaria (INT-001).
 *
 * <p>Mesma exceção para "não existe" e "é de outra cervejaria", deliberadamente: distinguir as duas
 * responderia a pergunta "este código existe em algum lugar do sistema?" para quem não deveria poder
 * fazê-la.
 */
public class UnknownDeviceException extends RuntimeException {

    public UnknownDeviceException(String code) {
        super("dispositivo desconhecido: " + code);
    }
}
