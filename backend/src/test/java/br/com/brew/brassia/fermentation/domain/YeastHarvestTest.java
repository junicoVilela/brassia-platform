package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YeastHarvestTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID STRAIN = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-07-31T10:00:00Z");

    private static YeastHarvest collect(UUID parentId, Integer parentGeneration) {
        return YeastHarvest.collect(BREWERY, "LV-001", STRAIN, BATCH, parentId, parentGeneration, AT,
                new BigDecimal("92.5"), "Creme limpo, sem odor", "Câmara fria 1", new BigDecimal("4"));
    }

    @Test
    void startsInQuarantineAndIsNotAvailableYet() {
        var harvest = collect(null, null);

        assertThat(harvest.status()).isEqualTo(YeastHarvestStatus.QUARANTINE);
        assertThat(harvest.available()).isFalse();
        assertThat(harvest.reviewedAt()).isNull();
    }

    @Test
    void freshPurchasedYeastIsGenerationOne() {
        assertThat(collect(null, null).generation()).isEqualTo(1);
    }

    @Test
    void derivesGenerationFromParentHarvest() {
        assertThat(collect(UUID.randomUUID(), 1).generation()).isEqualTo(2);
        assertThat(collect(UUID.randomUUID(), 4).generation()).isEqualTo(5);
    }

    @Test
    void rejectsInconsistentGenealogy() {
        // Geração e genealogia não podem divergir: uma nunca vem sem a outra.
        assertThatThrownBy(() -> collect(null, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem coleta-mãe");
        assertThatThrownBy(() -> collect(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem geração");
    }

    @Test
    void approvalMakesItAvailable() {
        var harvest = collect(null, null);
        harvest.approve(ACTOR, "Viabilidade boa", AT);

        assertThat(harvest.available()).isTrue();
        assertThat(harvest.status()).isEqualTo(YeastHarvestStatus.APPROVED);
        assertThat(harvest.reviewedBy()).isEqualTo(ACTOR);
        assertThat(harvest.reviewedAt()).isEqualTo(AT);
    }

    @Test
    void contaminatedHarvestIsRejectedAndStaysUnavailable() {
        var harvest = collect(null, null);
        harvest.reject(ACTOR, "Contaminação por lactobacillus", AT);

        assertThat(harvest.available()).isFalse();
        assertThat(harvest.status()).isEqualTo(YeastHarvestStatus.REJECTED);
        assertThat(harvest.reviewNote()).contains("lactobacillus");
    }

    @Test
    void rejectionRequiresReason() {
        var harvest = collect(null, null);
        assertThatThrownBy(() -> harvest.reject(ACTOR, "  ", AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
        assertThat(harvest.status()).isEqualTo(YeastHarvestStatus.QUARANTINE);
    }

    @Test
    void reviewIsTerminal() {
        var rejected = collect(null, null);
        rejected.reject(ACTOR, "Contaminação", AT);
        // Reprovada não volta a ficar disponível.
        assertThatThrownBy(() -> rejected.approve(ACTOR, "mudei de ideia", AT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("já revisada");

        var approved = collect(null, null);
        approved.approve(ACTOR, null, AT);
        assertThatThrownBy(() -> approved.reject(ACTOR, "tarde demais", AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesViabilityRange() {
        assertThatThrownBy(() -> YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT,
                new BigDecimal("100.1"), "ok", "Câmara", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("viabilidade");
        assertThatThrownBy(() -> YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT,
                new BigDecimal("-1"), "ok", "Câmara", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        // Fronteiras são válidas.
        assertThat(YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT, BigDecimal.ZERO, "ok",
                "Câmara", BigDecimal.ZERO).viabilityPercent()).isEqualByComparingTo("0");
        assertThat(YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT, new BigDecimal("100"), "ok",
                "Câmara", BigDecimal.ZERO).viabilityPercent()).isEqualByComparingTo("100");
    }

    @Test
    void requiresOriginAndStorage() {
        assertThatThrownBy(() -> YeastHarvest.collect(BREWERY, "LV", STRAIN, null, null, null, AT,
                new BigDecimal("90"), "ok", "Câmara", BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT,
                new BigDecimal("90"), "ok", " ", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("local de armazenamento");
        assertThatThrownBy(() -> YeastHarvest.collect(BREWERY, "LV", STRAIN, BATCH, null, null, AT,
                new BigDecimal("90"), " ", "Câmara", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("condição");
    }
}
