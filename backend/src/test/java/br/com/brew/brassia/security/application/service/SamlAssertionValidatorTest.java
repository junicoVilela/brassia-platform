package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.security.application.port.outbound.MfaSecretCipher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SamlAssertionValidatorTest {
    private final SamlAssertionValidator validator = new SamlAssertionValidator();

    @Test
    void acceptsValidAssertion() {
        var now = Instant.parse("2026-01-01T12:00:00Z");
        validator.validate(
                new SamlAssertionValidator.Assertion("issuer", "aud", "dest", now.minusSeconds(30), now.plusSeconds(30)),
                new SamlAssertionValidator.Context("issuer", "aud", "dest", now));
    }

    @Test
    void rejectsInvalidIssuer() {
        var now = Instant.now();
        assertThatThrownBy(() -> validator.validate(
                new SamlAssertionValidator.Assertion("wrong", "aud", "dest", now.minusSeconds(30), now.plusSeconds(30)),
                new SamlAssertionValidator.Context("issuer", "aud", "dest", now)))
                .hasMessageContaining("issuer");
    }

    @Test
    void parsesSigningCertificate() {
        var pem = """
                -----BEGIN CERTIFICATE-----
                MIIBkTCB+wIJAKHBfpE6gST+MA0GCSqGSIb3DQEBCwUAMBQxEjAQBgNVBAMMCWxv
                Y2FsaG9zdDAeFw0yNDAxMDEwMDAwMDBaFw0yNTAxMDEwMDAwMDBaMBQxEjAQBgNV
                BAMMCWxvY2FsaG9zdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABK8vqJ8r0n0k
                0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k0n0k
                -----END CERTIFICATE-----
                """;
        assertThatThrownBy(() -> validator.validateSigningCertificatePem(pem)).isInstanceOf(IllegalArgumentException.class);
    }
}
