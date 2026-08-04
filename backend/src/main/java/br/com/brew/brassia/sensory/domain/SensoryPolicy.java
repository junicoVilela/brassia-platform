package br.com.brew.brassia.sensory.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Política sensorial da cervejaria (PRM-001): a nota máxima da ficha.
 *
 * <p>Casas que usam a escala BJCP trabalham com 50 pontos; outras, com 10. O conjunto de atributos
 * continua fixo — parametrizá-lo reestruturaria a ficha, e isso ficou explicitamente fora desta
 * história.
 *
 * <p>A escala é <strong>congelada na sessão</strong> quando ela é criada. Mudar o parâmetro depois
 * não reinterpreta sessão nenhuma: uma nota 8 dada numa sessão de escala 10 não vira 8 de 50.
 */
public final class SensoryPolicy {

    /** Escala anterior à PRM-001; é o que as sessões já existentes usam. */
    public static final int DEFAULT_MAX_SCORE = 10;

    private final UUID breweryId;
    private int maxScore;
    private final long version;

    private SensoryPolicy(UUID breweryId, int maxScore, long version) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.maxScore = requireScale(maxScore);
        this.version = version;
    }

    public static SensoryPolicy defaults(UUID breweryId) {
        return new SensoryPolicy(breweryId, DEFAULT_MAX_SCORE, 0);
    }

    public static SensoryPolicy reconstitute(UUID breweryId, int maxScore, long version) {
        return new SensoryPolicy(breweryId, maxScore, version);
    }

    public void setMaxScore(int maxScore) {
        this.maxScore = requireScale(maxScore);
    }

    public UUID breweryId() {
        return breweryId;
    }

    public int maxScore() {
        return maxScore;
    }

    public long version() {
        return version;
    }

    private static int requireScale(int maxScore) {
        if (maxScore < 3) {
            throw new IllegalArgumentException("uma escala com menos de três pontos não discrimina nada");
        }
        if (maxScore > 100) {
            throw new IllegalArgumentException("escala acima de 100 pontos não é ficha sensorial");
        }
        return maxScore;
    }
}
