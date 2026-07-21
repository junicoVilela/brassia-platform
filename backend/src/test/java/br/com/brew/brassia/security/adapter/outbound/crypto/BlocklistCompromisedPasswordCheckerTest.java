package br.com.brew.brassia.security.adapter.outbound.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlocklistCompromisedPasswordCheckerTest {

    private final BlocklistCompromisedPasswordChecker checker = new BlocklistCompromisedPasswordChecker();

    @Test
    void flagsCommonPasswordsCaseInsensitive() {
        assertThat(checker.isCompromised("password")).isTrue();
        assertThat(checker.isCompromised("PASSWORD")).isTrue();
        assertThat(checker.isCompromised("  Senha123 ")).isTrue();
        assertThat(checker.isCompromised("brassia123")).isTrue();
    }

    @Test
    void allowsStrongUncommonPassword() {
        assertThat(checker.isCompromised("v3lha-caravela-do-mar-2026")).isFalse();
        assertThat(checker.isCompromised(null)).isFalse();
    }
}
