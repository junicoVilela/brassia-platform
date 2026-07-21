package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void normalizesTrimmingAndLowercasing() {
        var email = new EmailAddress("  Brewer@Example.COM ");
        assertThat(email.value()).isEqualTo("Brewer@Example.COM");
        assertThat(email.normalized()).isEqualTo("brewer@example.com");
    }

    @Test
    void rejectsBlankOrMalformed() {
        assertThatThrownBy(() -> new EmailAddress("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("no-at-sign")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("a@b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress("a@@b.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAboveMaxLength() {
        var local = "a".repeat(250);
        assertThatThrownBy(() -> new EmailAddress(local + "@x.com")).isInstanceOf(IllegalArgumentException.class);
    }
}
