package br.com.brew.brassia.community.domain;

/**
 * O que alguém escreveu numa publicação (COM-004).
 *
 * <p>Os dois são texto de gente de fora, e a diferença é o que se espera do autor: um comentário não
 * pede resposta, uma sugestão pede decisão. Separar é o que permite a tela mostrar "3 sugestões
 * pendentes" sem contar elogios junto.
 */
public enum ContributionKind {

    /** Uma observação. Não tem ciclo de vida além da moderação. */
    COMMENT,

    /**
     * Uma proposta de mudança, que o autor aceita ou recusa.
     *
     * <p><strong>Aceitar não altera nada.</strong> Ver {@link Contribution#accept}.
     */
    SUGGESTION
}
