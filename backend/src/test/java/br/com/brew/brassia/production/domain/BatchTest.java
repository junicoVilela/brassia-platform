package br.com.brew.brassia.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchTest {

    private static Batch open(String code, List<BatchStep> steps) {
        return Batch.open(UUID.randomUUID(), UUID.randomUUID(), code, UUID.randomUUID(), 1, "IPA",
                new BigDecimal("400"), Instant.now(), UUID.randomUUID(), steps);
    }

    @Test
    void opensInProgressPreservingSteps() {
        var steps = List.of(BatchStep.of(1, BatchStepType.MASH, "Mostura"),
                BatchStep.of(2, BatchStepType.TRANSFER, "Transferência"));
        var batch = open("OP-2026-0001", steps);

        assertThat(batch.status()).isEqualTo(BatchStatus.IN_PROGRESS);
        assertThat(batch.steps()).hasSize(2);
        assertThat(batch.steps().get(0).type()).isEqualTo(BatchStepType.MASH);
    }

    @Test
    void rejectsBlankCode() {
        assertThatThrownBy(() -> open("  ", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stepLabelFallsBackToType() {
        var step = BatchStep.of(1, BatchStepType.BOIL, "  ");
        assertThat(step.label()).isEqualTo("BOIL");
    }
}
