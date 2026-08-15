package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.production.OpenBatchLookup;
import br.com.brew.brassia.quality.application.port.outbound.FrequencySweepRepository;
import br.com.brew.brassia.quality.domain.FrequencyWindow;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Varre a cadência dos pontos de controle e avisa o que está atrasado (QLT-001-A).
 *
 * <p><strong>Avisa, não bloqueia</strong> — decisão do mantenedor. Parar a produção por um controle
 * atrasado pararia a fábrica por causa de uma medição, e quem opera passaria a burlar a regra em vez de
 * cumpri-la. O alerta entra na central do lote, onde o desvio grave (QLT-001) e a etapa atrasada
 * (FER-004) já aparecem — uma segunda central seria um segundo lugar para ninguém olhar.
 *
 * <p><strong>O ator é o sistema.</strong> Não há pessoa por trás de uma varredura, e emprestar o nome de
 * alguém a ela faria a trilha dizer que um humano avisou.
 */
public final class FrequencySweepService {

    /** Ator das varreduras: identificador fixo, para a trilha distinguir sistema de gente. */
    public static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private final FrequencySweepRepository sweep;
    private final OpenBatchLookup batches;
    private final BatchAlertPublisher alerts;
    private final Clock clock;

    public FrequencySweepService(FrequencySweepRepository sweep, OpenBatchLookup batches,
            BatchAlertPublisher alerts, Clock clock) {
        this.sweep = Objects.requireNonNull(sweep, "sweep");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @return quantos avisos novos foram abertos nesta passagem */
    public int sweep() {
        var now = clock.instant();
        var opened = 0;
        for (var breweryId : sweep.breweriesWithPublishedPlans()) {
            for (var batch : batches.openBatches(breweryId)) {
                opened += sweepBatch(breweryId, batch, now);
            }
        }
        return opened;
    }

    private int sweepBatch(UUID breweryId, OpenBatchLookup.OpenBatch batch, java.time.Instant now) {
        var opened = 0;
        for (var point : sweep.hourlyPointsFor(breweryId, batch.recipeId())) {
            var window = FrequencyWindow.of("PER_HOURS", point.everyHours()).orElseThrow();
            var last = sweep.lastMeasuredAt(breweryId, point.pointId(), batch.batchId());
            if (!window.isLate(last, batch.startedAt(), now)) {
                continue;
            }
            var missedWindow = window.dueAfter(last, batch.startedAt());
            // A janela perdida entra na chave: reavisar só acontece quando uma janela NOVA é perdida,
            // que é informação nova. O mesmo atraso não vira aviso a cada passagem do agendador.
            if (!sweep.recordAlert(breweryId, point.pointId(), batch.batchId(), missedWindow, now)) {
                continue;
            }
            alerts.openStepAlert(breweryId, SYSTEM_ACTOR, batch.batchId(), message(point, missedWindow),
                    missedWindow, now);
            opened++;
        }
        return opened;
    }

    /** O texto diz o parâmetro, a severidade e desde quando — sem os três, não dá para priorizar. */
    private static String message(FrequencySweepRepository.HourlyPoint point,
            java.time.Instant missedWindow) {
        return (point.critical() ? "Controle crítico atrasado: " : "Controle atrasado: ")
                + point.parameter() + " deveria ter sido medido até " + missedWindow
                + " (cadência de " + point.everyHours() + " h, severidade " + point.severity() + ").";
    }
}
