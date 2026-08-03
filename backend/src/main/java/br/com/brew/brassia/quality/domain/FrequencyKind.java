package br.com.brew.brassia.quality.domain;

/**
 * Cadência declarada da medição.
 *
 * <p>Nesta história a frequência é <strong>registrada, não fiscalizada</strong>: o plano diz de
 * quanto em quanto se mede, mas ninguém é avisado de medição atrasada — isso pede varredura
 * agendada, que é o mesmo débito aberto desde FER-004 (débito QLT-001-A).
 */
public enum FrequencyKind {
    PER_BATCH("A cada lote"),
    PER_HOURS("A cada N horas"),
    PER_SHIFT("A cada turno"),
    PER_PACKAGING_RUN("A cada envase");

    private final String label;

    FrequencyKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean needsValue() {
        return this == PER_HOURS;
    }
}
