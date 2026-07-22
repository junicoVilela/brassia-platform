package br.com.brew.brassia.security.application.port.outbound;

/** Cifra/decifra segredos MFA em repouso (AES-GCM). */
public interface MfaSecretCipher {
    EncryptedSecret encrypt(String plaintext);

    String decrypt(EncryptedSecret encrypted);

    record EncryptedSecret(String ciphertext, int keyVersion) {}
}
