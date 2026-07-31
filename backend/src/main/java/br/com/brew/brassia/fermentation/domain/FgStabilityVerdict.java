package br.com.brew.brassia.fermentation.domain;

/** Conclusão de uma avaliação de estabilidade de FG (FER-003). */
public enum FgStabilityVerdict {
    /** Série cobre a janela e varia dentro da tolerância. */
    STABLE,
    /** Leituras de densidade válidas em SG insuficientes para o critério. */
    INSUFFICIENT_READINGS,
    /** As leituras existem, mas não cobrem a janela — é o FG falso estável. */
    WINDOW_NOT_COVERED,
    /** A série ainda varia acima da tolerância. */
    VARIATION_ABOVE_TOLERANCE;

    public boolean stable() {
        return this == STABLE;
    }
}
