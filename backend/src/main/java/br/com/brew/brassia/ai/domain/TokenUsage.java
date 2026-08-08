package br.com.brew.brassia.ai.domain;

/**
 * Tokens consumidos por uma chamada, separados por direção porque o preço é separado por direção
 * (AIA-001). Entrada e saída somadas num número só perderiam a informação que explica a conta.
 */
public record TokenUsage(long inputTokens, long outputTokens) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);

    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("tokens não podem ser negativos");
        }
    }

    public long total() {
        return inputTokens + outputTokens;
    }
}
