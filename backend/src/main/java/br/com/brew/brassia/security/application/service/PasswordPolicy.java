package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.outbound.CompromisedPasswordChecker;
import br.com.brew.brassia.security.domain.RawPassword;
import java.util.Objects;

/**
 * Política de senha: comprimento (garantido pelo {@link RawPassword}) e rejeição
 * de senhas comprometidas/triviais. Sem classes de complexidade nem expiração —
 * aceita Unicode e frases (NIST).
 */
public final class PasswordPolicy {
    private final CompromisedPasswordChecker compromisedChecker;

    public PasswordPolicy(CompromisedPasswordChecker compromisedChecker) {
        this.compromisedChecker = Objects.requireNonNull(compromisedChecker);
    }

    /** @throws IllegalArgumentException se a senha for comprometida (mensagem segura). */
    public void validate(RawPassword password) {
        if (compromisedChecker.isCompromised(password.value())) {
            throw new IllegalArgumentException("senha muito comum ou comprometida; escolha outra");
        }
    }
}
