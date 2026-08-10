package br.com.brew.brassia.sensory.domain;

/**
 * O que se pode fazer com o conteúdo de uma fonte (SEN-002).
 *
 * <p>Os limiares de percepção são o ponto sensível: são o resultado de trabalho experimental caro, e é por
 * eles que os catálogos de referência cobram. Descrever "papelão" é vocabulário comum; afirmar que o
 * limiar do trans-2-nonenal é 0,1 µg/L é reproduzir uma medição de alguém.
 */
public enum LicenseTier {

    /** Redigido pela cervejaria. Sem restrição, sem atribuição. */
    OWN(false, true),

    /** Domínio público ou licença livre com atribuição — reproduzível citando a fonte. */
    ATTRIBUTION_REQUIRED(true, true),

    /**
     * Licenciado com restrição de redistribuição.
     *
     * <p>O vocabulário pode ser usado internamente; o dado quantitativo **não sai** em exportação. É a
     * diferença entre consultar um catálogo e republicá-lo.
     */
    LICENSED_INTERNAL_ONLY(true, false);

    private final boolean requiresAttribution;
    private final boolean allowsQuantitativeData;

    LicenseTier(boolean requiresAttribution, boolean allowsQuantitativeData) {
        this.requiresAttribution = requiresAttribution;
        this.allowsQuantitativeData = allowsQuantitativeData;
    }

    public boolean requiresAttribution() {
        return requiresAttribution;
    }

    public boolean allowsQuantitativeData() {
        return allowsQuantitativeData;
    }
}
