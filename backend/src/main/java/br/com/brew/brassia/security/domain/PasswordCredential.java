package br.com.brew.brassia.security.domain;

import java.util.Objects;

/** Credencial de senha de um usuário: somente o hash (com o id do encoder). */
public record PasswordCredential(UserId userId, String passwordHash, String encoderId) {
    public PasswordCredential {
        Objects.requireNonNull(userId);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("password hash é obrigatório");
        }
        if (encoderId == null || encoderId.isBlank()) {
            throw new IllegalArgumentException("encoder id é obrigatório");
        }
    }
}
