package br.com.brew.brassia.sensor.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Atraso de uma leitura: quanto tempo passou entre medir e chegar (INT-001).
 *
 * <p><strong>Atraso é sobre a chegada, qualidade é sobre o valor.</strong> São eixos independentes de
 * propósito: uma leitura pode estar perfeita e ter chegado três horas depois porque o gateway ficou sem
 * rede, e outra pode chegar instantaneamente com o sensor fora d'água. Misturar as duas num "status" só
 * obrigaria a escolher qual das duas informações perder.
 *
 * <p><strong>A régua é o intervalo esperado do dispositivo, não uma constante.</strong> Trinta segundos de
 * atraso não significam nada num sensor que reporta de hora em hora e significam uma janela inteira perdida
 * num que reporta a cada quinze segundos. Sem intervalo cadastrado não há régua, e então o atraso é medido
 * e informado mas não julgado — {@link #late()} é {@code false} porque não há base para afirmar o
 * contrário, e afirmar atraso sem régua seria inventar um limiar.
 */
public record Lateness(Duration delay, boolean late) {

    public Lateness {
        Objects.requireNonNull(delay, "delay");
    }

    /**
     * Calcula o atraso entre a medição e a chegada.
     *
     * <p>Medida do futuro produz atraso negativo. Ele é preservado como está — normalizar para zero
     * apagaria a evidência do relógio adiantado, que é justamente o que {@link ReadingQuality#FUTURE_CLOCK}
     * precisa que fique visível. Negativo nunca é "atrasado".
     */
    public static Lateness between(Instant measuredAt, Instant receivedAt, Duration expectedInterval) {
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        var delay = Duration.between(measuredAt, receivedAt);
        if (expectedInterval == null || delay.isNegative()) {
            return new Lateness(delay, false);
        }
        return new Lateness(delay, delay.compareTo(expectedInterval) > 0);
    }
}
