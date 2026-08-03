package br.com.brew.brassia.quality.domain;

/**
 * Corretiva trata a <em>ocorrência</em>; preventiva trata a <em>causa</em>.
 *
 * <p>São coisas diferentes e o registro as separa de propósito: descartar o lote afetado é
 * corretivo e não impede o problema de voltar. Um CAPA só com ação corretiva é um CAPA que vai se
 * repetir.
 */
public enum CapaActionKind {
    CORRECTIVE("Corretiva"),
    PREVENTIVE("Preventiva");

    private final String label;

    CapaActionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
