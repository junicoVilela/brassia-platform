package br.com.brew.brassia.security.domain;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP RFC 6238 (HMAC-SHA1, 6 dígitos, período 30s). Sem dependências externas;
 * janela ±1 para tolerância de relógio.
 */
public final class Totp {
    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {}

    /** Gera segredo Base32 (20 bytes de entropia). */
    public static String generateSecret() {
        var buffer = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(buffer);
        return encodeBase32(buffer);
    }

    public static String buildOtpAuthUri(String secret, String account, String issuer) {
        var label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    public static boolean verify(String secretBase32, String code, long epochSeconds) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        var key = decodeBase32(secretBase32);
        var step = epochSeconds / PERIOD_SECONDS;
        for (var offset = -WINDOW; offset <= WINDOW; offset++) {
            if (generateCode(key, step + offset) == Integer.parseInt(code)) {
                return true;
            }
        }
        return false;
    }

    public static boolean verify(String secretBase32, String code) {
        return verify(secretBase32, code, System.currentTimeMillis() / 1000L);
    }

    /** Gera o código atual (útil em testes). */
    public static String currentCode(String secretBase32, long epochSeconds) {
        var key = decodeBase32(secretBase32);
        var step = epochSeconds / PERIOD_SECONDS;
        return String.format("%0" + DIGITS + "d", generateCode(key, step));
    }

    private static int generateCode(byte[] key, long timeStep) {
        var data = ByteBuffer.allocate(8).putLong(timeStep).array();
        try {
            var mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            var hash = mac.doFinal(data);
            var offset = hash[hash.length - 1] & 0x0F;
            var binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return binary % (int) Math.pow(10, DIGITS);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("falha ao gerar TOTP", e);
        }
    }

    static String encodeBase32(byte[] data) {
        var sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return sb.toString();
    }

    static byte[] decodeBase32(String encoded) {
        var normalized = encoded.replace("=", "").toUpperCase();
        var output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : normalized.toCharArray()) {
            int value = c >= 'A' && c <= 'Z' ? c - 'A' : c - '2' + 26;
            if (value < 0 || value > 31) {
                throw new IllegalArgumentException("caractere Base32 inválido");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        if (index == output.length) {
            return output;
        }
        var trimmed = new byte[index];
        System.arraycopy(output, 0, trimmed, 0, index);
        return trimmed;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
