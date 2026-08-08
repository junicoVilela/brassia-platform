package br.com.brew.brassia.knowledge.domain;

/**
 * Um trecho recuperável de um documento (RAG-001).
 *
 * <p>{@code ordinal} é a posição no documento, e existe para que a citação seja localizável: "trecho 4
 * do manual da bomba" permite conferir; "algum lugar do manual da bomba" não permite. É também o que
 * torna a ordem reprodutível — dois trechos vizinhos citados juntos aparecem na ordem em que o
 * documento os escreveu, não na ordem em que a busca os pontuou.
 */
public record DocumentChunk(int ordinal, String text) {

    public DocumentChunk {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal não pode ser negativo");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("trecho vazio não é trecho");
        }
        text = text.strip();
    }
}
