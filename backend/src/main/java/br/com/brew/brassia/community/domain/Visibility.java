package br.com.brew.brassia.community.domain;

/**
 * Quem enxerga uma receita publicada (COM-001).
 *
 * <p><strong>A ordem dos valores é do mais fechado para o mais aberto, e isso não é decoração:</strong>
 * o plano de testes pede uma "matriz de visibilidade", e uma escala ordenada permite escrever a regra
 * como comparação em vez de uma cadeia de {@code if} que alguém vai esquecer de atualizar quando aparecer
 * um nível novo.
 */
public enum Visibility {

    /**
     * Só o autor. É o padrão de tudo que se cria — publicar é ato, e não estado inicial.
     */
    PRIVATE,

    /** A cervejaria do autor. Continua dado interno: não sai para busca nem para link. */
    BREWERY,

    /**
     * Quem tem o link.
     *
     * <p>Não aparece em busca. É o "mandei para um amigo avaliar" — e é o nível em que a revogação
     * importa mais, porque o link circula sem controle depois de compartilhado.
     */
    LINK,

    /**
     * Acessível por endereço direto, fora da busca e do feed.
     *
     * <p>Existe separado de {@link #LINK} porque são coisas diferentes: aqui não há segredo a revogar, a
     * receita simplesmente não é anunciada.
     */
    UNLISTED,

    /** Na biblioteca pública, na busca e no feed. */
    PUBLIC;

    /** Se este nível aparece em busca e feed. */
    public boolean listed() {
        return this == PUBLIC;
    }

    /** Se este nível é alcançável por quem não é da cervejaria do autor. */
    public boolean reachableFromOutside() {
        return ordinal() >= LINK.ordinal();
    }
}
