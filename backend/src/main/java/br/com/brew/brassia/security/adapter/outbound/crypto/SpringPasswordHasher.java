package br.com.brew.brassia.security.adapter.outbound.crypto;

import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
final class SpringPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;

    SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override public String hash(CharSequence rawPassword) { return encoder.encode(rawPassword); }
    @Override public boolean matches(CharSequence rawPassword, String encoded) { return encoder.matches(rawPassword, encoded); }
    @Override public boolean needsUpgrade(String encoded) { return encoder.upgradeEncoding(encoded); }
}
