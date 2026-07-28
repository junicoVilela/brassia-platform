package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FermentationProfileTest {

    private static final UUID BREWERY = UUID.randomUUID();

    private static FermentationStage timeStage(int seq, int days) {
        return FermentationStage.of(seq, "Primária", new BigDecimal("18.0"), 4, null, AdvanceCondition.TIME, days,
                null, true);
    }

    @Test
    void draftAllowsUpdateAndKeepsUniqueSequences() {
        var profile = FermentationProfile.draft(BREWERY, "ALE-STD", "Ale padrão", 1, List.of(timeStage(1, 5)));
        assertThat(profile.draftStatus()).isTrue();
        profile.update("Ale padrão v2", List.of(timeStage(1, 6), timeStage(2, 3)));
        assertThat(profile.stages()).hasSize(2);
    }

    @Test
    void rejectsDuplicateStageSequences() {
        assertThatThrownBy(() -> FermentationProfile.draft(BREWERY, "ALE", "Ale", 1,
                List.of(timeStage(1, 5), timeStage(1, 3)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishedProfileIsImmutable() {
        var published = FermentationProfile.reconstitute(ProfileId.newId(), BREWERY, "ALE", "Ale", 1,
                ProfileStatus.PUBLISHED, List.of(timeStage(1, 5)));
        assertThatThrownBy(() -> published.update("x", List.of(timeStage(1, 5))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeStageRequiresDaysAndRejectsGravity() {
        assertThatThrownBy(() -> FermentationStage.of(1, "P", new BigDecimal("18"), null, null,
                AdvanceCondition.TIME, null, null, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FermentationStage.of(1, "P", new BigDecimal("18"), null, null,
                AdvanceCondition.TIME, 5, new BigDecimal("1.010"), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gravityStageRequiresTargetGravity() {
        var ok = FermentationStage.of(1, "Diacetil", new BigDecimal("20"), null, null, AdvanceCondition.GRAVITY,
                null, new BigDecimal("1.012"), true);
        assertThat(ok.targetGravity()).isEqualByComparingTo("1.012");
        assertThatThrownBy(() -> FermentationStage.of(1, "D", new BigDecimal("20"), null, null,
                AdvanceCondition.GRAVITY, null, null, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualStageRejectsConditionFields() {
        assertThatThrownBy(() -> FermentationStage.of(1, "Cold crash", new BigDecimal("2"), 6, null,
                AdvanceCondition.MANUAL, 3, null, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCondition() {
        assertThatThrownBy(() -> AdvanceCondition.of("automatico")).isInstanceOf(IllegalArgumentException.class);
    }
}
