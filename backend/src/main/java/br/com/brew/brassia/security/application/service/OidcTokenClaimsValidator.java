package br.com.brew.brassia.security.application.service;

import java.util.Objects;

/** Valida claims OIDC estruturais (SEC-015). */
public final class OidcTokenClaimsValidator {
    public record Claims(String issuer, String subject, String nonce, String state, String codeChallenge) {}
    public record Context(String expectedIssuer, String expectedNonce, String expectedState, String expectedChallenge) {}

    public void validate(Claims claims, Context context) {
        Objects.requireNonNull(claims);
        Objects.requireNonNull(context);
        if (!context.expectedIssuer().equals(claims.issuer())) {
            throw new IllegalArgumentException("issuer OIDC inválido");
        }
        if (claims.subject() == null || claims.subject().isBlank()) {
            throw new IllegalArgumentException("sub OIDC obrigatório");
        }
        if (!context.expectedNonce().equals(claims.nonce())) {
            throw new IllegalArgumentException("nonce OIDC inválido");
        }
        if (!context.expectedState().equals(claims.state())) {
            throw new IllegalArgumentException("state OIDC inválido");
        }
        if (claims.codeChallenge() == null || !claims.codeChallenge().equals(context.expectedChallenge())) {
            throw new IllegalArgumentException("PKCE inválido");
        }
    }

    public void validateProviderConfig(String issuerUrl, String clientId) {
        if (issuerUrl == null || !issuerUrl.startsWith("https://")) {
            throw new IllegalArgumentException("issuer URL OIDC inválido");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId OIDC obrigatório");
        }
    }
}
