package br.com.brew.brassia.quality.domain;

/** Etapa do processo a que o plano de controle se aplica. */
public enum ProcessStage {
    BREWING("Brassagem"),
    FERMENTATION("Fermentação"),
    MATURATION("Maturação"),
    PACKAGING("Envase"),
    STORAGE("Estocagem");

    private final String label;

    ProcessStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
