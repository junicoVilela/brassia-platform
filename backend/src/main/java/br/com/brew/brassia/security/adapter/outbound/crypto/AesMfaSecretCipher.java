package br.com.brew.brassia.security.adapter.outbound.crypto;

import br.com.brew.brassia.security.application.port.outbound.MfaSecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM para segredos TOTP; chave Base64 de 32 bytes via configuração. */
@Component
final class AesMfaSecretCipher implements MfaSecretCipher {
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final int KEY_VERSION = 1;

    private final byte[] key;

    AesMfaSecretCipher(@Value("${brassia.security.mfa.secret-key}") String base64Key) {
        this.key = Base64.getDecoder().decode(base64Key);
        if (this.key.length != 32) {
            throw new IllegalStateException("brassia.security.mfa.secret-key deve ter 32 bytes");
        }
    }

    @Override
    public EncryptedSecret encrypt(String plaintext) {
        try {
            var nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var packed = ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
            return new EncryptedSecret(Base64.getEncoder().encodeToString(packed), KEY_VERSION);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao cifrar segredo MFA", e);
        }
    }

    @Override
    public String decrypt(EncryptedSecret encrypted) {
        if (encrypted.keyVersion() != KEY_VERSION) {
            throw new IllegalStateException("versão de chave MFA não suportada");
        }
        try {
            var packed = Base64.getDecoder().decode(encrypted.ciphertext());
            var nonce = new byte[NONCE_BYTES];
            System.arraycopy(packed, 0, nonce, 0, NONCE_BYTES);
            var ciphertext = new byte[packed.length - NONCE_BYTES];
            System.arraycopy(packed, NONCE_BYTES, ciphertext, 0, ciphertext.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao decifrar segredo MFA", e);
        }
    }
}
