package br.com.brew.brassia.gas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GasConnectionTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID CYLINDER = UUID.randomUUID();
    private static final UUID POINT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-08-01T10:00:00Z");

    private static GasNetworkComponent regulator(String maxBar, String setBar) {
        return GasNetworkComponent.register(BREWERY, ComponentKind.REGULATOR, "REG-1", "Regulador CO2",
                new BigDecimal(maxBar), new BigDecimal(setBar));
    }

    private static GasNetworkComponent manifold(String maxBar) {
        return GasNetworkComponent.register(BREWERY, ComponentKind.MANIFOLD, "MAN-1", "Manifold 4 vias",
                new BigDecimal(maxBar), null);
    }

    private static GasConnection connect(String workingBar, GasNetworkComponent manifold) {
        return GasConnection.connect(BREWERY, CYLINDER, regulator("10", "3"), manifold, POINT,
                new BigDecimal(workingBar), AT, ACTOR);
    }

    private static LeakTest passed() {
        return new LeakTest(true, "espuma + queda de pressão", BigDecimal.ZERO, null, ACTOR, AT);
    }

    @Test
    void startsPendingTestAndDoesNotServeYet() {
        var connection = connect("3", null);

        assertThat(connection.status()).isEqualTo(ConnectionStatus.PENDING_TEST);
        assertThatThrownBy(connection::requireServing).isInstanceOf(IllegalStateException.class);
        assertThat(connection.open()).isTrue();
    }

    @Test
    void networkCeilingIsTheWeakestComponent() {
        // Regulador aguenta 10 bar, manifold só 6: a rede toda vale 6.
        var connection = connect("3", manifold("6"));

        assertThat(connection.networkMaxPressureBar()).isEqualByComparingTo("6");
    }

    @Test
    void refusesWorkingPressureAboveTheNetworkCeiling() {
        assertThatThrownBy(() -> connect("8", manifold("6")))
                .isInstanceOf(GasConnectionBlockedException.class)
                .extracting(e -> ((GasConnectionBlockedException) e).blockers().getFirst().code())
                .isEqualTo("working_pressure_above_network");
    }

    @Test
    void servesOnlyAfterAPassingLeakTest() {
        var connection = connect("3", null);

        connection.recordLeakTest(passed());

        assertThat(connection.status()).isEqualTo(ConnectionStatus.SERVING);
        connection.requireServing();
    }

    @Test
    void failedLeakTestBlocksTheLineAndRequiresANote() {
        var connection = connect("3", null);

        assertThatThrownBy(() -> new LeakTest(false, "espuma", new BigDecimal("0.4"), null, ACTOR, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige observação");

        connection.recordLeakTest(new LeakTest(false, "espuma", new BigDecimal("0.4"), "bolhas na conexão do "
                + "regulador", ACTOR, AT));

        assertThat(connection.status()).isEqualTo(ConnectionStatus.BLOCKED);
        assertThatThrownBy(connection::requireServing).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void overPressureReadingBlocksTheLine() {
        var connection = connect("3", manifold("6"));
        connection.recordLeakTest(passed());

        assertThat(connection.evaluatePressure(new BigDecimal("6"))).isFalse();
        assertThat(connection.status()).isEqualTo(ConnectionStatus.SERVING);

        assertThat(connection.evaluatePressure(new BigDecimal("6.5"))).isTrue();
        assertThat(connection.status()).isEqualTo(ConnectionStatus.BLOCKED);
        // Bloqueio de segurança não se resolve com outra leitura.
        assertThatThrownBy(() -> connection.evaluatePressure(new BigDecimal("3")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blockedLineGoesBackToServiceOnlyAfterANewPassingTest() {
        var connection = connect("3", null);
        connection.recordLeakTest(passed());
        connection.evaluatePressure(new BigDecimal("12"));
        assertThat(connection.status()).isEqualTo(ConnectionStatus.BLOCKED);

        connection.recordLeakTest(passed());

        assertThat(connection.status()).isEqualTo(ConnectionStatus.SERVING);
    }

    @Test
    void disconnectRequiresReasonAndIsTerminal() {
        var connection = connect("3", null);
        connection.recordLeakTest(passed());

        assertThatThrownBy(() -> connection.disconnect(" ", AT)).isInstanceOf(IllegalArgumentException.class);

        connection.disconnect("troca de cilindro", AT);
        assertThat(connection.status()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(connection.open()).isFalse();
        assertThatThrownBy(() -> connection.disconnect("de novo", AT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> connection.recordLeakTest(passed())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void regulatorCannotBeSetAboveItsOwnLimit() {
        assertThatThrownBy(() -> regulator("10", "12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acima da pressão máxima");
    }

    @Test
    void manifoldHasNoSetPressure() {
        assertThat(manifold("6").setPressureBar()).isNull();
        assertThatThrownBy(() -> GasNetworkComponent.register(BREWERY, ComponentKind.MANIFOLD, "MAN-2",
                "Manifold", new BigDecimal("6"), new BigDecimal("3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifold não tem pressão ajustada");
    }
}
