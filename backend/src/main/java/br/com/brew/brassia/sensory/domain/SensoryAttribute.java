package br.com.brew.brassia.sensory.domain;

/**
 * Atributos da ficha (SEN-001). A nota vai de zero até a escala da sessão.
 *
 * <p>O conjunto é fixo; a biblioteca de descritores e off-flavors (SEN-002) é que vai enriquecer a
 * ficha. A <strong>escala</strong> deixou de ser constante na PRM-001: ela é parâmetro da
 * cervejaria, congelado na sessão — casas que usam BJCP trabalham com 50 pontos.
 */
public enum SensoryAttribute {
    APPEARANCE("Aparência"),
    AROMA("Aroma"),
    FLAVOR("Sabor"),
    BODY("Corpo"),
    OVERALL("Impressão global");

    public static final int MIN_SCORE = 0;

    private final String label;

    SensoryAttribute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
