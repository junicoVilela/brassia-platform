package br.com.brew.brassia.metrology.domain;

/** Tipo do instrumento; define o que ele mede, não como é calibrado. */
public enum InstrumentType {
    THERMOMETER("Termômetro"),
    HYDROMETER("Densímetro"),
    PH_METER("pHmetro"),
    SCALE("Balança"),
    PRESSURE_GAUGE("Manômetro"),
    OXYGEN_METER("Medidor de oxigênio"),
    FLOW_METER("Medidor de vazão");

    private final String label;

    InstrumentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
