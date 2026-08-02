package br.com.brew.brassia.gas.domain;

/**
 * Situação da conexão de gás (GAS-001). Nasce em {@code PENDING_TEST}: só serve depois de um teste
 * de vazamento aprovado. {@code BLOCKED} é a parada de segurança — teste reprovado ou sobrepressão
 * medida — e exige intervenção humana; a conexão não volta a servir sozinha.
 */
public enum ConnectionStatus {
    PENDING_TEST,
    SERVING,
    BLOCKED,
    DISCONNECTED;

    public boolean open() {
        return this != DISCONNECTED;
    }
}
