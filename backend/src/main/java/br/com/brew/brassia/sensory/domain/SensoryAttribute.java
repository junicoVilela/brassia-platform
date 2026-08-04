package br.com.brew.brassia.sensory.domain;

/**
 * Atributos da ficha (SEN-001), com nota de 0 a 10.
 *
 * <p>O conjunto é fixo nesta história; a biblioteca de descritores e off-flavors (SEN-002) é que
 * vai enriquecer a ficha. Conjunto e escala são candidatos naturais à parametrização por
 * cervejaria (PRM-001) — casas que usam BJCP de 50 pontos precisam de outra escala.
 */
public enum SensoryAttribute {
    APPEARANCE("Aparência"),
    AROMA("Aroma"),
    FLAVOR("Sabor"),
    BODY("Corpo"),
    OVERALL("Impressão global");

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;

    private final String label;

    SensoryAttribute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
