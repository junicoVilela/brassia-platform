package br.com.brew.brassia.packaging.domain;

import java.util.Locale;

/**
 * Campos que um rótulo pode carregar (PKG-004). Cada um tem uma origem rastreável no sistema —
 * nada é digitado no rótulo, tudo vem do lote, do plano, da receita ou do controle de frescor.
 *
 * <p>O conjunto de campos é do sistema; <strong>quais deles são obrigatórios é regra regulatória</strong>
 * e vive à parte ({@link LabelRegulatoryRule}). Misturar as duas coisas faria uma troca de layout
 * derrubar silenciosamente um campo exigido por lei.
 */
public enum LabelField {
    /** Nome da cerveja, congelado no lote no momento da abertura. */
    BEER_NAME,
    /** Código do lote de produção. */
    BATCH_CODE,
    /** Volume nominal da embalagem. */
    VOLUME_ML,
    /** Teor alcoólico calculado da receita publicada. */
    ABV,
    /** Alergênicos declarados dos ingredientes. */
    ALLERGENS,
    /** Validade vigente, recomendada pela evidência de oxigênio ou sobreposta. */
    BEST_BEFORE,
    /** Payload do QR, que aponta para a rastreabilidade do lote. */
    QR_PAYLOAD;

    public static LabelField of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("campo do rótulo é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("campo de rótulo inválido: " + raw);
        }
    }
}
