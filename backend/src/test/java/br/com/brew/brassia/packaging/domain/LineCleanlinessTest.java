package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LineCleanlinessTest {

    private static final Instant PLANNED_START = Instant.parse("2026-08-05T09:00:00Z");
    private static final Instant YESTERDAY = Instant.parse("2026-08-04T18:00:00Z");
    private static final Instant LAST_WEEK = Instant.parse("2026-07-29T08:00:00Z");

    @Test
    void blocksLineThatWasNeverCleaned() {
        var blocker = LineCleanliness.check(null, null, PLANNED_START, true);

        assertThat(blocker).get().extracting(PackagingBlockedException.Blocker::code).isEqualTo("line_not_clean");
    }

    @Test
    void acceptsCleanLineWithoutPreviousUse() {
        assertThat(LineCleanliness.check(YESTERDAY, null, PLANNED_START, true)).isEmpty();
    }

    @Test
    void blocksCleaningReleasedAfterThePlannedStart() {
        var afterStart = PLANNED_START.plusSeconds(3600);

        assertThat(LineCleanliness.check(afterStart, null, PLANNED_START, true)).isPresent();
    }

    @Test
    void acceptsCleaningReleasedExactlyAtThePlannedStart() {
        assertThat(LineCleanliness.check(PLANNED_START, null, PLANNED_START, true)).isEmpty();
    }

    @Test
    void blocksWhenTheLineWasUsedAgainAfterTheLastCleaning() {
        // Limpeza da semana passada não cobre um envase que ocupou a linha ontem.
        assertThat(LineCleanliness.check(LAST_WEEK, YESTERDAY, PLANNED_START, true)).isPresent();
        // Nem quando o uso é simultâneo à liberação.
        assertThat(LineCleanliness.check(YESTERDAY, YESTERDAY, PLANNED_START, true)).isPresent();
    }

    @Test
    void blocksWhenTheCleaningReleaseExpiredByPolicy() {
        // A validade por tempo é parâmetro da cervejaria (PRM-001): aqui a linha só recebe o
        // veredito de "já não cobre", sem saber quantas horas foram configuradas.
        var blocker = LineCleanliness.check(YESTERDAY, LAST_WEEK, PLANNED_START, false);

        assertThat(blocker).get().extracting(PackagingBlockedException.Blocker::message).asString()
                .contains("venceu");
    }

    @Test
    void acceptsCleaningDoneAfterThePreviousPackagingRun() {
        assertThat(LineCleanliness.check(YESTERDAY, LAST_WEEK, PLANNED_START, true)).isEmpty();
    }
}
