package br.com.brew.brassia.sensor.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O atraso de uma leitura (INT-001).
 *
 * <p>O que estes testes fixam é que atraso é medido contra a régua do dispositivo. Um limiar fixo diria
 * que trinta segundos é atraso tanto para um sensor de hora em hora quanto para um de quinze segundos — e
 * estaria errado nos dois casos, por motivos opostos.
 */
class LatenessTest {

    private static final Instant MEDIU = Instant.parse("2026-08-09T10:00:00Z");

    @Test
    @DisplayName("chegada dentro do intervalo esperado não é atraso")
    void dentroDoIntervaloNaoEAtraso() {
        var lateness = Lateness.between(MEDIU, MEDIU.plusSeconds(60), Duration.ofMinutes(5));

        assertThat(lateness.delay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(lateness.late()).isFalse();
    }

    @Test
    @DisplayName("chegada além do intervalo esperado é atraso: a janela seguinte já deveria ter chegado")
    void alemDoIntervaloEAtraso() {
        var lateness = Lateness.between(MEDIU, MEDIU.plus(Duration.ofMinutes(12)), Duration.ofMinutes(5));

        assertThat(lateness.delay()).isEqualTo(Duration.ofMinutes(12));
        assertThat(lateness.late()).isTrue();
    }

    @Test
    @DisplayName("exatamente no limite ainda não é atraso")
    void noLimiteNaoEAtraso() {
        // O limite é "mais que o intervalo", não "a partir do intervalo". Um dispositivo que reporta a cada
        // 5 min e cuja mensagem leva exatamente 5 min não perdeu janela nenhuma — a próxima chega na hora.
        var lateness = Lateness.between(MEDIU, MEDIU.plus(Duration.ofMinutes(5)), Duration.ofMinutes(5));

        assertThat(lateness.late()).isFalse();
    }

    @Test
    @DisplayName("sem intervalo cadastrado o atraso é medido mas não é julgado")
    void semIntervaloNaoJulga() {
        // Não há régua, então afirmar atraso seria inventar um limiar. O número continua visível: quem
        // olhar a leitura vê que ela levou três horas para chegar, e decide o que isso significa.
        var lateness = Lateness.between(MEDIU, MEDIU.plus(Duration.ofHours(3)), null);

        assertThat(lateness.delay()).isEqualTo(Duration.ofHours(3));
        assertThat(lateness.late()).isFalse();
    }

    @Test
    @DisplayName("medida do futuro produz atraso negativo, preservado, e nunca é 'atrasada'")
    void futuroPreservaNegativoENaoEAtraso() {
        // Normalizar para zero apagaria a evidência do relógio adiantado — que é exatamente o que
        // ReadingQuality.FUTURE_CLOCK precisa que fique visível.
        var lateness = Lateness.between(MEDIU, MEDIU.minusSeconds(90), Duration.ofMinutes(1));

        assertThat(lateness.delay()).isEqualTo(Duration.ofSeconds(-90));
        assertThat(lateness.late()).isFalse();
    }
}
