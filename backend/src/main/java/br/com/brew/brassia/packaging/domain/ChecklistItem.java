package br.com.brew.brassia.packaging.domain;

import java.util.Locale;

/**
 * Itens do checklist de envase (PKG-001) que dependem de confirmação humana na linha.
 *
 * <p>Limpeza da linha não está aqui de propósito: ela é verificada contra o ciclo de
 * sanitização liberado (evidência rastreável), não contra um "ok" digitado.
 */
public enum ChecklistItem {
    /** Embalagem inspecionada: integridade, lote correto e ausência de contaminação visível. */
    CONTAINER_INSPECTED("embalagem inspecionada"),
    /** Vedação/recravação testada na amostra inicial. */
    SEAL_TEST("vedação testada"),
    /** Suprimento de gás conectado e com pressão conferida. */
    GAS_SUPPLY("gás conectado e conferido");

    private final String label;

    ChecklistItem(String label) {
        this.label = label;
    }

    /** Texto para a mensagem de bloqueio; o código do item continua sendo o contrato estável. */
    public String label() {
        return label;
    }

    public static ChecklistItem of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("item do checklist é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("item de checklist inválido");
        }
    }
}
