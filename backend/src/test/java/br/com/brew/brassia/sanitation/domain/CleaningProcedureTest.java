package br.com.brew.brassia.sanitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleaningProcedureTest {

    private static ProcedureStep step(int seq, String min, String max) {
        return ProcedureStep.of(seq, "CIP", "soda cáustica", new BigDecimal(min), new BigDecimal(max),
                new BigDecimal("60"), new BigDecimal("80"), 20, "recirculação", "luvas nitrílicas",
                "detergente enzimático", "não misturar com ácido", true);
    }

    @Test
    void draftsWithSteps() {
        var p = CleaningProcedure.draft(UUID.randomUUID(), "CIP-TANK", "CIP de tanque", 1,
                List.of(step(1, "1", "2"), step(2, "0.5", "1")));
        assertThat(p.status()).isEqualTo(ProcedureStatus.DRAFT);
        assertThat(p.steps()).hasSize(2);
        assertThat(p.version()).isEqualTo(1);
    }

    @Test
    void rejectsInvertedConcentrationRange() {
        assertThatThrownBy(() -> step(1, "2", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateSequences() {
        assertThatThrownBy(() -> CleaningProcedure.draft(UUID.randomUUID(), "C", "N", 1,
                List.of(step(1, "1", "2"), step(1, "1", "2"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishedIsImmutable() {
        var p = CleaningProcedure.reconstitute(ProcedureId.newId(), UUID.randomUUID(), "C", "N", 1,
                ProcedureStatus.PUBLISHED, List.of(step(1, "1", "2")));
        assertThatThrownBy(() -> p.update("Novo", List.of(step(1, "1", "2"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
