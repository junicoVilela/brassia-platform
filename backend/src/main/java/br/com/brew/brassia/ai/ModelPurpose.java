package br.com.brew.brassia.ai;

/**
 * Para que a chamada ao modelo serviu (AIA-001).
 *
 * <p>É o eixo pelo qual custo e falha são lidos depois: "gastamos R$ 40 em IA" não ajuda ninguém a
 * decidir nada, "R$ 38 dos R$ 40 foram em verificação de conectividade" ajuda. Cada história que
 * passa a usar o gateway acrescenta o seu propósito aqui.
 */
public enum ModelPurpose {

    /** Verificação de conectividade e contrato — o menor pedido possível que ainda prova o caminho. */
    CONNECTIVITY_PROBE
}
