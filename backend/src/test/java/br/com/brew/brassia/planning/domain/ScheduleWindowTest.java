package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleWindowTest {

    private static final Instant T0 = Instant.parse("2026-08-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-08-01T12:00:00Z");
    private static final Instant T6 = Instant.parse("2026-08-01T14:00:00Z");

    @Test
    void rejectsEndBeforeOrEqualStart() {
        assertThatThrownBy(() -> new ScheduleWindow(T2, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScheduleWindow(T2, T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlappingWindowsConflict() {
        var a = new ScheduleWindow(T0, T4);
        var b = new ScheduleWindow(T2, T6);
        assertThat(a.overlaps(b)).isTrue();
        assertThat(b.overlaps(a)).isTrue();
    }

    @Test
    void containedWindowConflicts() {
        var outer = new ScheduleWindow(T0, T6);
        var inner = new ScheduleWindow(T2, T4);
        assertThat(outer.overlaps(inner)).isTrue();
        assertThat(inner.overlaps(outer)).isTrue();
    }

    @Test
    void adjacentWindowsDoNotConflict() {
        var a = new ScheduleWindow(T0, T2);
        var b = new ScheduleWindow(T2, T4);
        assertThat(a.overlaps(b)).isFalse();
        assertThat(b.overlaps(a)).isFalse();
    }

    @Test
    void disjointWindowsDoNotConflict() {
        var a = new ScheduleWindow(T0, T2);
        var b = new ScheduleWindow(T4, T6);
        assertThat(a.overlaps(b)).isFalse();
    }
}
