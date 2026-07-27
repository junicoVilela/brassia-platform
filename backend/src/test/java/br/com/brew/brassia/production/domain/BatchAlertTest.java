package br.com.brew.brassia.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchAlertTest {

    private static BatchAlert open(String message) {
        return BatchAlert.open(UUID.randomUUID(), UUID.randomUUID(), BatchAlertKind.DECISION, message, null, null,
                Instant.now(), UUID.randomUUID());
    }

    @Test
    void opensPending() {
        var a = open("Decidir sobre correção de OG");
        assertThat(a.status()).isEqualTo(BatchAlertStatus.PENDING);
        assertThat(a.confirmed()).isFalse();
        assertThat(a.kind()).isEqualTo(BatchAlertKind.DECISION);
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> open("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesKindCaseInsensitively() {
        assertThat(BatchAlertKind.of("addition")).isEqualTo(BatchAlertKind.ADDITION);
        assertThatThrownBy(() -> BatchAlertKind.of("noise")).isInstanceOf(IllegalArgumentException.class);
    }
}
