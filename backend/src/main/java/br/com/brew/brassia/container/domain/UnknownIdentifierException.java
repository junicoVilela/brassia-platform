package br.com.brew.brassia.container.domain;

import java.util.UUID;

/**
 * Não há etiqueta ativa com esse identificador para aposentar (DEB-CON-003 #6).
 *
 * <p><strong>Etiqueta não é vasilhame.</strong> Devolver {@code container_not_found} aqui mandaria o
 * operador procurar um keg que existe e está bem — o que não foi encontrado é o adesivo. É a mesma
 * separação que o módulo já faz entre "o vasilhame não pode" e "a cerveja não pode": nomear a coisa
 * errada faz a pessoa trocar a coisa errada.
 *
 * <p>Cobre três casos de propósito, sem distingui-los: identificador que nunca existiu, já aposentado, ou
 * de outra cervejaria. Separá-los diria a quem tenta adivinhar quais etiquetas existem na casa ao lado.
 */
public class UnknownIdentifierException extends RuntimeException {

    public UnknownIdentifierException(UUID id) {
        super("Nenhuma etiqueta ativa " + id + " para aposentar.");
    }
}
