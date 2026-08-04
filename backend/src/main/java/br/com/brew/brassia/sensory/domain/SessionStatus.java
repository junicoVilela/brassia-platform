package br.com.brew.brassia.sensory.domain;

/**
 * Situação da sessão sensorial (SEN-001).
 *
 * <p>É o estado que controla a cegueira: enquanto {@code OPEN}, nenhuma nota e nenhum lote são
 * visíveis. Só {@code CLOSED} revela — e revelar antes é o caminho mais curto para o painel
 * convergir por contágio em vez de por concordância.
 */
public enum SessionStatus {
    DRAFT("Rascunho"),
    OPEN("Em avaliação"),
    CLOSED("Encerrada");

    private final String label;

    SessionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean revealsResults() {
        return this == CLOSED;
    }

    public boolean acceptsEvaluation() {
        return this == OPEN;
    }
}
