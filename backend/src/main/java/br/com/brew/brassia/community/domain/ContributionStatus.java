package br.com.brew.brassia.community.domain;

/** Onde está uma sugestão (COM-004). */
public enum ContributionStatus {

    /** Ainda sem decisão. Comentário nasce e morre aqui — ele não é decidido. */
    OPEN,

    /**
     * O autor concordou.
     *
     * <p>Concordar é diferente de aplicar: o que muda a receita é o autor mexer nela.
     */
    ACCEPTED,

    /** O autor não vai seguir. A sugestão continua visível: recusar não é apagar. */
    DECLINED
}
