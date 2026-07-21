package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    void trimsAndAcceptsWithinBounds() {
        assertThat(new DisplayName("  Ana Cervejeira  ").value()).isEqualTo("Ana Cervejeira");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new DisplayName("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAboveMaxLength() {
        var tooLong = "a".repeat(161);
        assertThatThrownBy(() -> new DisplayName(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }
}
