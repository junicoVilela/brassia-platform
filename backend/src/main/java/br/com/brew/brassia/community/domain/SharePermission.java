package br.com.brew.brassia.community.domain;

/**
 * O que o link autoriza (COM-002).
 *
 * <p><strong>Nenhum nível permite editar a receita.</strong> Um link é um convite de leitura ou de
 * conversa — nunca uma chave para o conteúdo interno. Quem quer que outro mexa na receita cria um fork
 * (COM-003), e aí a alteração vive na cópia dele.
 */
public enum SharePermission {

    /** Só ler o retrato publicado. */
    READ,

    /**
     * Ler e comentar (COM-004).
     *
     * <p>Comentar é escrever <em>sobre</em> a receita, e não <em>na</em> receita — a diferença é o que
     * mantém a autoria intacta.
     */
    COMMENT
}
