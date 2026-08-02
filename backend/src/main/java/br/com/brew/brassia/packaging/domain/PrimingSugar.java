package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Açúcar de priming e quanto CO₂ ele rende (PKG-002), em gramas de CO₂ por grama de açúcar.
 *
 * <p>Sacarose e dextrose vêm da estequiometria da fermentação, então são exatas: sacarose
 * (C₁₂H₂₂O₁₁, 342,3 g/mol) rende 4 CO₂ (176,04 g) → 0,514; dextrose mono-hidratada
 * (C₆H₁₂O₆·H₂O, 198,17 g/mol) rende 2 CO₂ (88,02 g) → 0,444.
 *
 * <p>O extrato seco de malte é <strong>aproximado</strong>: o rendimento depende da fermentabilidade
 * do extrato, que varia por fabricante e lote. Quem escolhe DME recebe o aviso junto do cálculo,
 * em vez de um número com precisão que ele não tem.
 */
public enum PrimingSugar {
    SUCROSE("0.514", false),
    DEXTROSE_MONOHYDRATE("0.444", false),
    DRY_MALT_EXTRACT("0.400", true);

    private final BigDecimal yieldGramsCo2PerGram;
    private final boolean approximate;

    PrimingSugar(String yieldGramsCo2PerGram, boolean approximate) {
        this.yieldGramsCo2PerGram = new BigDecimal(yieldGramsCo2PerGram);
        this.approximate = approximate;
    }

    public BigDecimal yieldGramsCo2PerGram() {
        return yieldGramsCo2PerGram;
    }

    /** Rendimento estimado, não estequiométrico: o resultado sai com aviso de confiança menor. */
    public boolean approximate() {
        return approximate;
    }

    public static PrimingSugar of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("açúcar de priming é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("açúcar de priming inválido");
        }
    }
}
