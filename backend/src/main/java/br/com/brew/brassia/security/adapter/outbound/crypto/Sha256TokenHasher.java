package br.com.brew.brassia.security.adapter.outbound.crypto;

import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Hash SHA-256 (hex) para tokens de conta. Adequado porque o token é aleatório
 * de alta entropia; usar bcrypt/argon (do encoder de senha) seria desnecessário
 * e caro por verificação.
 */
@Component
final class Sha256TokenHasher implements TokenHasher {

    @Override
    public String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
