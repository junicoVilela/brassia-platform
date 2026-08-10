package br.com.brew.brassia.blend.domain;

import java.math.BigDecimal;

/**
 * O balanço não fecha (BLD-001).
 *
 * <p><strong>Recusar é o ponto.</strong> Cerveja que entra e não sai foi para algum lugar: perda de
 * transferência, sobra num tanque, erro de medição. Aceitar a diferença em silêncio criaria volume do nada
 * — e volume do nada vira cerveja envasada que a rastreabilidade não sabe de onde veio.
 *
 * <p>A perda declarada é o caminho legítimo: quem sabe que perdeu 12 litros na transferência declara os 12
 * litros. O que não se pode é a conta não fechar sem ninguém dizer por quê.
 */
public final class UnbalancedBlendException extends RuntimeException {

    private final BigDecimal inputLiters;
    private final BigDecimal outputLiters;
    private final BigDecimal declaredLoss;
    private final BigDecimal difference;

    UnbalancedBlendException(BigDecimal inputLiters, BigDecimal outputLiters, BigDecimal declaredLoss,
            BigDecimal difference) {
        super("balanço não fecha: entram " + inputLiters + " L, saem " + outputLiters
                + " L mais " + declaredLoss + " L de perda declarada — diferença de " + difference + " L");
        this.inputLiters = inputLiters;
        this.outputLiters = outputLiters;
        this.declaredLoss = declaredLoss;
        this.difference = difference;
    }

    public BigDecimal inputLiters() {
        return inputLiters;
    }

    public BigDecimal outputLiters() {
        return outputLiters;
    }

    public BigDecimal declaredLoss() {
        return declaredLoss;
    }

    /** Quanto falta explicar. Positivo: sumiu cerveja. Negativo: apareceu cerveja. */
    public BigDecimal difference() {
        return difference;
    }
}
