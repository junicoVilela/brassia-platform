package br.com.brew.brassia.referencedata.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Cálculo de checksum SHA-256 (hex) de um conteúdo, para dedup/idempotência. */
public final class Checksums {

    private Checksums() {
    }

    public static Checksum sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
            return new Checksum(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
