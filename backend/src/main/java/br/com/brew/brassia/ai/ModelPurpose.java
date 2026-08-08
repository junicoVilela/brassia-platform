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
    CONNECTIVITY_PROBE,

    /** Resposta a uma pergunta com base em trechos de documento recuperados (RAG-002). */
    GROUNDED_ANSWER,

    /** Avaliação de risco de um lote a partir dos fatos que o domínio calculou (AIA-002). */
    BATCH_ASSESSMENT,

    /** Proposta de comando, sempre sujeita a confirmação humana (AIA-003). */
    COMMAND_PROPOSAL
}
