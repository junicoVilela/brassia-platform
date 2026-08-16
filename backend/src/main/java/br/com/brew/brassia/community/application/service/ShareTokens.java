package br.com.brew.brassia.community.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gera e resume os tokens de link (COM-002).
 *
 * <p><strong>SHA-256 sem sal, e é decisão — não descuido.</strong> Sal existe para atrapalhar ataque de
 * dicionário contra segredo escolhido por gente; este token são 256 bits de aleatório, e não há
 * dicionário que o alcance. O que se ganha sem sal é poder <strong>buscar pelo hash</strong> — sem isso,
 * validar um link exigiria ler todos os links do banco e comparar um a um.
 *
 * <p>É a mesma razão pela qual senha usa hash lento com sal e token aleatório não usa: as duas coisas
 * defendem contra ataques diferentes.
 */
final class ShareTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareTokens() {
    }

    /** O valor legível, mostrado uma vez e nunca persistido. */
    static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder()
                    .encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
