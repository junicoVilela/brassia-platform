package br.com.brew.brassia.quality.domain;

/**
 * De onde a não conformidade veio.
 *
 * <p>Desvio é só uma das origens. Não conformidade também nasce de reclamação de cliente,
 * auditoria e problema de fornecedor — por isso ela é agregado próprio, e não um estado a mais
 * dentro de {@link Deviation}.
 */
public enum NonConformitySource {
    DEVIATION("Desvio de medição"),
    COMPLAINT("Reclamação de cliente"),
    AUDIT("Auditoria"),
    SUPPLIER("Fornecedor"),
    OTHER("Outra");

    private final String label;

    NonConformitySource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
