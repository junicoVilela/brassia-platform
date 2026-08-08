package br.com.brew.brassia.ai.domain;

/**
 * Em que ponto está uma proposta (AIA-003).
 *
 * <p>Não há estado "executado", e é deliberado: o aceite registra a decisão autorizada e entrega o comando a
 * quem tem alçada para praticá-lo na tela do módulo dono. Inventar aqui um "executado" que ninguém escreve
 * seria guardar uma afirmação que o sistema não consegue sustentar.
 *
 * <p>Vencida também não é estado gravado — é derivada do prazo. Gravá-la exigiria um processo varrendo a
 * tabela para mudar linhas, e uma proposta cujo prazo passou já é inaceitável sem que ninguém a marque.
 */
public enum ProposalStatus {

    /** Proposta feita, ninguém decidiu. */
    PENDING,

    /** Alguém com a alçada do comando confirmou. */
    ACCEPTED,

    /** Alguém descartou. Recusar não altera nada no sistema, e por isso não exige alçada do comando. */
    REJECTED
}
