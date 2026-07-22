package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpTest {

    @Test
    void generatesAndVerifiesWithinWindow() {
        var secret = Totp.generateSecret();
        var code = Totp.currentCode(secret, 1_700_000_000L);
        assertThat(Totp.verify(secret, code, 1_700_000_000L)).isTrue();
    }

    @Test
    void rejectsWrongCode() {
        var secret = Totp.generateSecret();
        assertThat(Totp.verify(secret, "000000", 1_700_000_000L)).isFalse();
    }
}
