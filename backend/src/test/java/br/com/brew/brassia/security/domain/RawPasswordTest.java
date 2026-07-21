package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RawPasswordTest {

    @Test
    void acceptsWithinBounds() {
        assertThat(new RawPassword("segredo1").value()).isEqualTo("segredo1");
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> new RawPassword("curta")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOrTooLong() {
        assertThatThrownBy(() -> new RawPassword(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RawPassword("a".repeat(201))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringDoesNotLeakValue() {
        assertThat(new RawPassword("segredo1").toString()).doesNotContain("segredo1");
    }
}
