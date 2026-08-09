package br.com.brew.brassia.integration.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura HMAC-SHA256 de uma entrega (INT-002).
 *
 * <p><strong>O que a assinatura prova e o que não prova.</strong> Ela prova que a mensagem saiu de quem
 * conhece o segredo e que o corpo não foi alterado no caminho. Não prova que a mensagem é recente — por
 * isso o instante entra <em>dentro</em> do que é assinado, e não só num cabeçalho ao lado: um cabeçalho
 * não assinado pode ser reescrito por quem intercepta, e aí um replay de ontem parece de agora.
 *
 * <p>O formato assinado é {@code <timestamp>.<corpo>}, com o ponto como separador. O separador não é
 * decoração: sem ele, {@code timestamp=1} + corpo {@code "23..."} e {@code timestamp=12} + corpo
 * {@code "3..."} produziriam a mesma entrada para o HMAC, e duas mensagens diferentes teriam a mesma
 * assinatura válida. O ponto não aparece em dígitos de época, então a divisão é sempre inequívoca.
 */
public final class WebhookSignature {

    /** Prefixo do algoritmo no cabeçalho: permite trocar de algoritmo sem quebrar quem já valida. */
    public static final String PREFIX = "sha256=";

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    /** Assina {@code <epochSeconds>.<payload>} e devolve {@code sha256=<hex>}. */
    public static String sign(String secret, long epochSeconds, String payload) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(payload, "payload");
        if (secret.isBlank()) {
            throw new IllegalArgumentException("segredo do webhook é obrigatório");
        }
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            var signed = epochSeconds + "." + payload;
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // Algoritmo fixo e chave sempre presente: chegar aqui é defeito de instalação da JVM, não
            // condição que o chamador possa tratar.
            throw new IllegalStateException("não foi possível assinar a entrega", e);
        }
    }

    /**
     * Compara duas assinaturas em tempo constante.
     *
     * <p>{@code String.equals} sai no primeiro byte diferente, e a diferença de tempo entre "errou no
     * primeiro caractere" e "errou no último" é medível pela rede. Quem tem paciência descobre a
     * assinatura correta um byte por vez sem nunca conhecer o segredo. Existe aqui porque é o mesmo
     * código que quem recebe o webhook deveria usar — e porque o nosso próprio teste precisa dele.
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
