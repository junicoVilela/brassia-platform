package br.com.brew.brassia.container.domain;

/**
 * A etiqueta já está viva em outro contêiner.
 *
 * <p>A garantia é o índice único parcial; isto é a tradução dele. Duas telas colando o mesmo adesivo em
 * kegs diferentes passariam por qualquer checagem prévia e deixariam a leitura ambígua para sempre.
 */
public class DuplicateIdentifierException extends RuntimeException {

    public DuplicateIdentifierException(String value) {
        super("A etiqueta " + value + " já está em uso por outro contêiner.");
    }
}
