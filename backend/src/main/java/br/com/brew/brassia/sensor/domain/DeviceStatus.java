package br.com.brew.brassia.sensor.domain;

/**
 * Estado operacional de um dispositivo (INT-001).
 *
 * <p>A diferença entre {@link #PAUSED} e {@link #REVOKED} é de intenção, e ela existe porque o caminho de
 * volta é diferente: pausar é manutenção — o dispositivo volta com a mesma identidade e a mesma série
 * histórica. Revogar é dizer que aquela identidade não é mais confiável, e voltar atrás exigiria decidir
 * que o que ela mandou no meio-tempo vale. Por isso revogado não retorna: cadastra-se outro.
 */
public enum DeviceStatus {

    ACTIVE,

    /** Fora de operação temporariamente. Leituras são recusadas; a identidade continua válida. */
    PAUSED,

    /** Identidade descontinuada. Terminal — não há transição de saída. */
    REVOKED;

    public boolean acceptsReadings() {
        return this == ACTIVE;
    }
}
