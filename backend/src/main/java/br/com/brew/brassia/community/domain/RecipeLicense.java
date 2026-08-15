package br.com.brew.brassia.community.domain;

/**
 * Sob que licença a receita é publicada (COM-001).
 *
 * <p><strong>Lista fechada, e não texto livre — ao contrário do canal de venda e da atividade de mão de
 * obra, que são cadastro.</strong> A diferença é que licença tem efeito jurídico: alguém que escrevesse
 * "livre" no campo estaria dizendo nada, e quem copiasse a receita acreditando naquilo ficaria exposto.
 * Uma lista curta de licenças conhecidas é o que permite a próxima pessoa saber o que pode fazer.
 *
 * <p>O aceite exige que a publicação mostre <strong>autor, licença, fonte e versão</strong> — e a licença
 * é a única das quatro que decide o que o leitor pode fazer com o que leu.
 */
public enum RecipeLicense {

    /** Domínio público: usar, alterar e redistribuir, sem exigir atribuição. */
    CC0("CC0 1.0"),

    /** Atribuição: usar e alterar, citando o autor. */
    CC_BY("CC BY 4.0"),

    /** Atribuição e compartilhamento igual: derivados carregam a mesma licença. */
    CC_BY_SA("CC BY-SA 4.0"),

    /** Atribuição, sem uso comercial. */
    CC_BY_NC("CC BY-NC 4.0"),

    /**
     * Todos os direitos reservados.
     *
     * <p>Publicar assim é legítimo: a cervejaria mostra a receita e não autoriza cópia. É o padrão
     * quando ninguém escolhe — porque o contrário, assumir permissão por omissão, dá ao público um
     * direito que o autor nunca concedeu.
     */
    ALL_RIGHTS_RESERVED("Todos os direitos reservados");

    private final String label;

    RecipeLicense(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Se a licença autoriza fork por terceiros (COM-003). */
    public boolean allowsDerivatives() {
        return this != ALL_RIGHTS_RESERVED;
    }
}
