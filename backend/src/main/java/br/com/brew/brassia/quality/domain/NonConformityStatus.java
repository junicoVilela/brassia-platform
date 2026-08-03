package br.com.brew.brassia.quality.domain;

/**
 * Fases do tratamento (QLT-002), em ordem.
 *
 * <p>A ordem não é burocracia: não se investiga o que não se conteve, não se age sem causa raiz e
 * não se verifica sem ação. Pular etapa é o jeito mais comum de um CAPA virar teatro — fica o
 * registro de que algo foi tratado sem que nada tenha sido.
 */
public enum NonConformityStatus {
    OPEN("Aberta"),
    CONTAINED("Contida"),
    INVESTIGATED("Investigada"),
    ACTION_PLANNED("Ação planejada"),
    VERIFIED("Verificada"),
    CLOSED("Encerrada");

    private final String label;

    NonConformityStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean terminal() {
        return this == CLOSED;
    }
}
