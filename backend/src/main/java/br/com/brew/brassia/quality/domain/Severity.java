package br.com.brew.brassia.quality.domain;

/**
 * Severidade do desvio, definida no ponto de controle e herdada pelo desvio que ele abre.
 *
 * <p>A severidade é do <em>parâmetro</em>, não da medição: quem decide o quanto importa sair da
 * faixa é quem escreveu o plano, antes de qualquer medida existir. Deixar a severidade para o
 * momento do desvio abriria espaço para minimizar o problema depois de ele acontecer.
 */
public enum Severity {
    MINOR("Leve"),
    MAJOR("Grave"),
    CRITICAL("Crítica");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Severidade que merece aviso na central do lote. */
    public boolean alertsBatch() {
        return this != MINOR;
    }
}
