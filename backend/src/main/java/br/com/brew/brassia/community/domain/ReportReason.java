package br.com.brew.brassia.community.domain;

/**
 * Por que alguém denunciou (COM-005).
 *
 * <p>Lista fechada e curta. "Outro" existe porque a lista nunca cobre tudo, e obrigar a escolher uma
 * categoria errada faria a estatística mentir sobre o que a comunidade reclama.
 */
public enum ReportReason {

    /** Conteúdo ofensivo ou impróprio. */
    ABUSE,

    /**
     * Cópia sem crédito.
     *
     * <p>É a denúncia que esta plataforma tem mais chance de receber: publicar receita de outro é fácil,
     * e a licença só vale se houver como reclamar.
     */
    PLAGIARISM,

    /** Propaganda ou conteúdo repetido. */
    SPAM,

    /** O que a lista não cobre — com o texto explicando. */
    OTHER
}
