package br.com.brew.brassia.metrology.domain;

/** Veredito do certificado de calibração. */
public enum CalibrationResult {
    APPROVED("Aprovado"),
    /**
     * Aprovado com restrição: serve, mas o certificado impõe uma condição (faixa útil menor,
     * uso limitado). A restrição é texto obrigatório e viaja junto da aptidão para quem consulta —
     * o sistema não a interpreta nem estreita a faixa sozinho (ver débito MTR-001-B).
     */
    APPROVED_WITH_RESTRICTION("Aprovado com restrição"),
    REJECTED("Reprovado");

    private final String label;

    CalibrationResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean approves() {
        return this != REJECTED;
    }
}
