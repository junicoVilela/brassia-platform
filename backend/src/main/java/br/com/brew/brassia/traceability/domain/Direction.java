package br.com.brew.brassia.traceability.domain;

/**
 * Sentido da travessia.
 *
 * <p>As duas perguntas da rastreabilidade não são a mesma. {@code BACKWARD} é "de onde veio esta
 * cerveja" — a pergunta da investigação. {@code FORWARD} é "para onde foi este insumo" — a pergunta
 * do recall. Uma responde por que algo deu errado; a outra, o que precisa ser recolhido.
 */
public enum Direction {

    /** Ancestrais: o que originou o nó. */
    BACKWARD,

    /** Descendentes: o que veio do nó. */
    FORWARD,

    /** Os dois lados, a partir do mesmo nó. */
    BOTH;

    public boolean includesBackward() {
        return this != FORWARD;
    }

    public boolean includesForward() {
        return this != BACKWARD;
    }
}
