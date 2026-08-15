package br.com.brew.brassia.quality.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A janela de cadência de um ponto de controle (QLT-001-A).
 *
 * <p><strong>Só a cadência por horas é julgável por relógio</strong>, e é a única que esta regra cobra:
 *
 * <ul>
 *   <li>{@code PER_HOURS} — o relógio decide. É esta.</li>
 *   <li>{@code PER_BATCH} — "uma vez por lote" só está atrasada quando o lote acaba sem a medição; não há
 *       instante durante o lote em que ela esteja atrasada.</li>
 *   <li>{@code PER_SHIFT} — a plataforma não tem calendário de turnos, e inventar um (8h a partir da
 *       meia-noite?) produziria atraso onde a cervejaria não vê atraso nenhum.</li>
 *   <li>{@code PER_PACKAGING_RUN} — a referência é a corrida de envase, não o lote em produção.</li>
 * </ul>
 *
 * <p>Cobrar as três últimas exigiria inventar o significado delas, que é o oposto do que este débito
 * pedia. Elas continuam declaradas e não fiscalizadas — e agora isso está dito, em vez de suposto.
 */
public record FrequencyWindow(Duration interval) {

    public FrequencyWindow {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("intervalo da cadência precisa ser positivo");
        }
    }

    /** A janela de um ponto por horas; vazio para as cadências que o relógio não julga. */
    public static Optional<FrequencyWindow> of(String frequencyKind, Integer everyHours) {
        if (!"PER_HOURS".equals(frequencyKind) || everyHours == null || everyHours <= 0) {
            return Optional.empty();
        }
        return Optional.of(new FrequencyWindow(Duration.ofHours(everyHours)));
    }

    /**
     * O instante em que a próxima medição era esperada.
     *
     * @param lastMeasuredAt a última medição do ponto no lote; nulo quando ainda não houve nenhuma, e aí
     *                       a contagem parte do início do lote — um lote de 30 horas sem a primeira
     *                       medição está tão atrasado quanto um que mediu e parou
     */
    public Instant dueAfter(Instant lastMeasuredAt, Instant batchStartedAt) {
        var reference = lastMeasuredAt != null ? lastMeasuredAt : batchStartedAt;
        return reference.plus(interval);
    }

    /** Está atrasada? O empate não conta: no instante exato do prazo, ela ainda está no prazo. */
    public boolean isLate(Instant lastMeasuredAt, Instant batchStartedAt, Instant now) {
        return now.isAfter(dueAfter(lastMeasuredAt, batchStartedAt));
    }
}
