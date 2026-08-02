package br.com.brew.brassia.gas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GasCylinderTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.parse("2026-08-01");

    private static GasCylinder cylinder(String contentKg, LocalDate dueOn) {
        return GasCylinder.register(BREWERY, "CIL-001", GasType.CO2, new BigDecimal("10"),
                new BigDecimal("12.5"), new BigDecimal(contentKg), dueOn, "Casa de gases");
    }

    private static GasCylinder full() {
        return cylinder("10", TODAY.plusYears(2));
    }

    @Test
    void startsAvailableWhenItHasGas() {
        var cylinder = full();

        assertThat(cylinder.status()).isEqualTo(CylinderStatus.AVAILABLE);
        assertThat(cylinder.expired(TODAY)).isFalse();
        assertThat(cylinder.blockers(TODAY)).isEmpty();
    }

    @Test
    void startsEmptyWhenRegisteredWithoutGas() {
        assertThat(cylinder("0", TODAY.plusYears(2)).status()).isEqualTo(CylinderStatus.EMPTY);
    }

    @Test
    void rejectsContentAboveCapacityAndNonPositiveMeasures() {
        assertThatThrownBy(() -> cylinder("11", TODAY.plusYears(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede a capacidade");
        assertThatThrownBy(() -> GasCylinder.register(BREWERY, "CIL-002", GasType.CO2, BigDecimal.ZERO,
                new BigDecimal("12.5"), BigDecimal.ZERO, TODAY.plusYears(2), "Casa"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredRequalificationBlocksAllocation() {
        var expired = cylinder("10", TODAY.minusDays(1));

        assertThat(expired.expired(TODAY)).isTrue();
        assertThat(expired.blockers(TODAY))
                .extracting(GasConnectionBlockedException.Blocker::code)
                .containsExactly("cylinder_expired");
        assertThatThrownBy(() -> expired.connect(TODAY)).isInstanceOf(GasConnectionBlockedException.class);
        assertThat(expired.status()).isEqualTo(CylinderStatus.AVAILABLE);
    }

    @Test
    void requalificationDueTodayStillAllowsAllocation() {
        var cylinder = cylinder("10", TODAY);

        assertThat(cylinder.expired(TODAY)).isFalse();
        cylinder.connect(TODAY);

        assertThat(cylinder.status()).isEqualTo(CylinderStatus.CONNECTED);
    }

    @Test
    void blockedCylinderIsNotAllocatedAndKeepsTheReason() {
        var cylinder = full();
        cylinder.block("válvula com folga");

        assertThat(cylinder.status()).isEqualTo(CylinderStatus.BLOCKED);
        assertThat(cylinder.blockReason()).isEqualTo("válvula com folga");
        assertThatThrownBy(() -> cylinder.connect(TODAY))
                .isInstanceOf(GasConnectionBlockedException.class)
                .extracting(e -> ((GasConnectionBlockedException) e).blockers().getFirst().code())
                .isEqualTo("cylinder_blocked");
    }

    @Test
    void blockingRequiresReasonAndDisconnectionFirst() {
        var cylinder = full();
        assertThatThrownBy(() -> cylinder.block(" ")).isInstanceOf(IllegalArgumentException.class);

        cylinder.connect(TODAY);
        assertThatThrownBy(() -> cylinder.block("avaria"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desconecte");
    }

    @Test
    void unblockingDoesNotEraseAnExpiredRequalification() {
        var cylinder = cylinder("10", TODAY.minusDays(1));
        cylinder.block("requalificação vencida");

        cylinder.unblock();

        assertThat(cylinder.status()).isEqualTo(CylinderStatus.AVAILABLE);
        // Desbloquear não requalifica: o vencimento continua impedindo o uso.
        assertThat(cylinder.blockers(TODAY))
                .extracting(GasConnectionBlockedException.Blocker::code)
                .containsExactly("cylinder_expired");
    }

    @Test
    void requalifyingRequiresAFutureDate() {
        var cylinder = cylinder("10", TODAY.minusDays(1));

        assertThatThrownBy(() -> cylinder.requalify(TODAY, TODAY)).isInstanceOf(IllegalArgumentException.class);

        cylinder.requalify(TODAY.plusYears(5), TODAY);
        assertThat(cylinder.expired(TODAY)).isFalse();
    }

    @Test
    void cylinderInUseIsNotAllocatedTwice() {
        var cylinder = full();
        cylinder.connect(TODAY);

        assertThat(cylinder.blockers(TODAY))
                .extracting(GasConnectionBlockedException.Blocker::code)
                .containsExactly("cylinder_in_use");
    }

    @Test
    void reportsEveryBlockerAtOnce() {
        var cylinder = cylinder("10", TODAY.minusDays(30));
        cylinder.block("avaria na rosca");

        assertThat(cylinder.blockers(TODAY))
                .extracting(GasConnectionBlockedException.Blocker::code)
                .containsExactlyInAnyOrder("cylinder_blocked", "cylinder_expired");
    }

    @Test
    void consumptionReducesContentAndEmptiesTheCylinder() {
        var cylinder = full();
        cylinder.connect(TODAY);

        cylinder.consume(new BigDecimal("4"));
        assertThat(cylinder.contentKg()).isEqualByComparingTo("6");

        cylinder.consume(new BigDecimal("6"));
        assertThat(cylinder.contentKg()).isEqualByComparingTo("0");
        // Continua conectado até alguém desconectar; ao liberar, vira vazio.
        assertThat(cylinder.status()).isEqualTo(CylinderStatus.CONNECTED);
        cylinder.release();
        assertThat(cylinder.status()).isEqualTo(CylinderStatus.EMPTY);
    }

    @Test
    void refusesConsumptionBeyondContentAndOutsideService() {
        var cylinder = full();
        assertThatThrownBy(() -> cylinder.consume(new BigDecimal("1")))
                .isInstanceOf(IllegalStateException.class);

        cylinder.connect(TODAY);
        assertThatThrownBy(() -> cylinder.consume(new BigDecimal("11")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que o conteúdo");
        assertThatThrownBy(() -> cylinder.consume(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cylinder.contentKg()).isEqualByComparingTo("10");
    }

    @Test
    void refillRestoresTheAvailableCylinder() {
        var cylinder = full();
        cylinder.connect(TODAY);
        cylinder.consume(new BigDecimal("10"));
        cylinder.release();

        cylinder.refill(new BigDecimal("9.8"));

        assertThat(cylinder.status()).isEqualTo(CylinderStatus.AVAILABLE);
        assertThat(cylinder.contentKg()).isEqualByComparingTo("9.8");
        assertThatThrownBy(() -> cylinder.refill(new BigDecimal("12")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
