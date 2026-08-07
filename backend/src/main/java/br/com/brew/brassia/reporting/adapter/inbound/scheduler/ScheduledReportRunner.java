package br.com.brew.brassia.reporting.adapter.inbound.scheduler;

import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.application.service.ReportExecutionService;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara as execuções programadas (RPT-003).
 *
 * <p><strong>Não guarda "quando rodou por último".</strong> Ele tenta a chave do período atual, e a
 * idempotência decide: se aquele dia (ou semana, ou mês) já tem execução, o repositório devolve a
 * que existe e nada é produzido de novo. Um marcador de última execução seria uma segunda verdade
 * que se perde numa restauração de backup — e aí o relatório sairia duas vezes ou nenhuma.
 *
 * <p>Isso também é o que faz o agendador ser seguro de rodar com frequência: chamar de hora em hora
 * um relatório diário produz um artefato por dia, não vinte e quatro.
 *
 * <p>Falha de uma definição não interrompe as outras. Aqui, ao contrário do painel, engolir é o
 * certo: uma cervejaria com definição quebrada não pode impedir as demais de receberem as suas.
 */
@Component
class ScheduledReportRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReportRunner.class);

    private final SavedReportRepository reports;
    private final ReportExecutionService execution;

    ScheduledReportRunner(SavedReportRepository reports, ReportExecutionService execution) {
        this.reports = Objects.requireNonNull(reports);
        this.execution = Objects.requireNonNull(execution);
    }

    @Scheduled(cron = "${brassia.reporting.schedule-cron:0 0 * * * *}")
    void run() {
        var now = Instant.now();
        for (var report : reports.findScheduled()) {
            try {
                execution.execute(report, ReportExecutionService.scheduledKey(report, now), now);
            } catch (RuntimeException ex) {
                log.warn("relatório programado {} falhou: {}", report.id(), ex.getMessage());
            }
        }
    }
}
