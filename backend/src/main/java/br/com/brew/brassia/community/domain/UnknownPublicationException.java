package br.com.brew.brassia.community.domain;

import java.util.UUID;

/**
 * Publicação que não existe, ou que quem perguntou não pode ver (COM-001).
 *
 * <p><strong>A mesma resposta para os dois casos, e aqui isso vale mais que nos outros módulos:</strong>
 * numa biblioteca, distinguir "não existe" de "é privada" permite enumerar o que as outras cervejarias
 * têm sem ler nada — basta contar quais identificadores respondem diferente.
 */
public class UnknownPublicationException extends RuntimeException {

    public UnknownPublicationException(UUID id) {
        super("a publicação " + id + " não foi encontrada");
    }
}
