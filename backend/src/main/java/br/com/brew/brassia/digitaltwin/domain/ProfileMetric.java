package br.com.brew.brassia.digitaltwin.domain;

/**
 * O que um perfil aprende (DTW-001).
 *
 * <p><strong>Cada métrica é uma grandeza observada, nunca uma explicação.</strong> "Perda na transferência"
 * é o que se mediu; "perda na transferência causada pelo trub" seria uma afirmação que os dados não
 * sustentam. A enum só admite a primeira forma, e é assim que a fronteira contra correlação-vira-causa fica
 * no tipo em vez de numa recomendação de code review.
 */
public enum ProfileMetric {

    /**
     * Quanto do volume planejado chegou ao fermentador, em percentual.
     *
     * <p>É rendimento observado, não eficiência de mostura: mede o caminho inteiro do dia de brassa, com
     * todas as perdas dentro. Chamá-lo de "eficiência" seria emprestar um nome técnico que descreve outra
     * coisa — quem lê "eficiência 74%" pensa em extração de açúcar do malte.
     */
    VOLUME_YIELD_PERCENT("Rendimento de volume (%)"),

    /** Perda declarada na transferência, em litros. */
    TRANSFER_LOSS_LITERS("Perda na transferência (L)");

    private final String label;

    ProfileMetric(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
