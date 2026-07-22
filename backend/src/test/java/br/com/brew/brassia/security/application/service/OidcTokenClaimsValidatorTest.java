package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OidcTokenClaimsValidatorTest {
    private final OidcTokenClaimsValidator validator = new OidcTokenClaimsValidator();

    @Test
    void validatesClaims() {
        validator.validate(
                new OidcTokenClaimsValidator.Claims("https://idp", "sub", "nonce", "state", "challenge"),
                new OidcTokenClaimsValidator.Context("https://idp", "nonce", "state", "challenge"));
    }

    @Test
    void rejectsInvalidNonce() {
        assertThatThrownBy(() -> validator.validate(
                new OidcTokenClaimsValidator.Claims("https://idp", "sub", "bad", "state", "challenge"),
                new OidcTokenClaimsValidator.Context("https://idp", "nonce", "state", "challenge")))
                .hasMessageContaining("nonce");
    }
}
