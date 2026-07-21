package br.com.brew.brassia.security.adapter.outbound.crypto;

import br.com.brew.brassia.security.application.port.outbound.CompromisedPasswordChecker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Blocklist offline de senhas comprometidas, carregada de
 * {@code security/common-passwords.txt}. Comparação case-insensitive.
 */
@Component
final class BlocklistCompromisedPasswordChecker implements CompromisedPasswordChecker {
    private final Set<String> blocklist;

    BlocklistCompromisedPasswordChecker() {
        this.blocklist = load();
    }

    @Override
    public boolean isCompromised(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return blocklist.contains(rawPassword.trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        var resource = new ClassPathResource("security/common-passwords.txt");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(line -> line.trim().toLowerCase(Locale.ROOT))
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao carregar blocklist de senhas", e);
        }
    }
}
