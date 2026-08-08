package br.com.brew.brassia.knowledge.domain;

/**
 * O que o documento é (RAG-001).
 *
 * <p>O tipo não é etiqueta: ele muda como a resposta deve ser lida. "O manual do fabricante diz" e
 * "o laudo do lote diz" têm autoridades diferentes sobre a mesma pergunta, e quem lê precisa saber
 * qual dos dois respondeu.
 *
 * <p><strong>POP de limpeza não está aqui de propósito.</strong> O procedimento de sanitização já é
 * estrutura versionada e imutável no módulo {@code sanitation}, e o sistema responde sobre ele de
 * forma determinística. Passá-lo por recuperação textual transformaria um fato em um palpite — o
 * contrário do princípio de que a IA interpreta e o domínio decide. O que entra aqui é o que não tem
 * estrutura: manual, ficha, laudo e nota.
 */
public enum DocumentType {

    /** Manual de fabricante: instalação, operação e manutenção de equipamento. */
    EQUIPMENT_MANUAL,

    /** Ficha de dados de segurança (FISPQ) de produto químico. */
    SAFETY_DATA_SHEET,

    /** Laudo de laboratório: análise de água, matéria-prima ou produto. */
    LAB_REPORT,

    /** Procedimento ou instrução operacional que não tem forma estruturada no sistema. */
    OPERATING_PROCEDURE,

    /** Nota técnica, boletim de fornecedor, ata — o que não cabe nos outros. */
    TECHNICAL_NOTE
}
