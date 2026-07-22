package br.com.brew.brassia.security.application.service;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** Valida campos estruturais de assertion SAML (SEC-014). */
public final class SamlAssertionValidator {
    public record Assertion(String issuer, String audience, String destination, Instant notBefore, Instant notOnOrAfter) {}
    public record Context(String expectedIssuer, String expectedAudience, String expectedDestination, Instant now) {}

    public void validate(Assertion assertion, Context context) {
        Objects.requireNonNull(assertion);
        Objects.requireNonNull(context);
        if (!context.expectedIssuer().equals(assertion.issuer())) {
            throw new IllegalArgumentException("issuer SAML inválido");
        }
        if (!context.expectedAudience().equals(assertion.audience())) {
            throw new IllegalArgumentException("audience SAML inválido");
        }
        if (!context.expectedDestination().equals(assertion.destination())) {
            throw new IllegalArgumentException("destination SAML inválido");
        }
        if (context.now().isBefore(assertion.notBefore()) || !context.now().isBefore(assertion.notOnOrAfter())) {
            throw new IllegalArgumentException("assertion SAML fora da janela temporal");
        }
    }

    public void validateSigningCertificatePem(String pem) {
        parseCertificate(pem);
    }

    static X509Certificate parseCertificate(String pem) {
        try {
            var normalized = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            var bytes = Base64.getDecoder().decode(normalized);
            var factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("certificado PEM inválido");
        }
    }
}
