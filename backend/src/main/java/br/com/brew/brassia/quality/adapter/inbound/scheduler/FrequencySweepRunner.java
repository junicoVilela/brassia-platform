package br.com.brew.brassia.quality.adapter.inbound.scheduler;

import br.com.brew.brassia.quality.application.service.FrequencySweepService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passa de hora em hora atrás de controle atrasado (QLT-001-A).
 *
 * <p><strong>Não guarda "quando rodou por último"</strong>, como o agendador de relatórios: a memória do
 * que já foi avisado é a restrição única da janela perdida. Um marcador de última execução seria uma
 * segunda verdade que se perde numa restauração de backup — e aí o atraso passaria em branco ou viraria
 * aviso repetido.
 *
 * <p>De hora em hora, e não de minuto em minuto: a menor cadência que um ponto pode declarar é de uma
 * hora, então varrer mais vezes só encontraria o mesmo atraso mais cedo dentro da mesma hora.
 */
@Component
class FrequencySweepRunner {

    private static final Logger log = LoggerFactory.getLogger(FrequencySweepRunner.class);

    private final FrequencySweepService sweep;

    FrequencySweepRunner(FrequencySweepService sweep) {
        this.sweep = Objects.requireNonNull(sweep, "sweep");
    }

    @Scheduled(cron = "${brassia.quality.frequency-sweep-cron:0 5 * * * *}")
    void run() {
        try {
            var opened = sweep.sweep();
            if (opened > 0) {
                log.info("varredura de cadência abriu {} alerta(s) de controle atrasado", opened);
            }
        } catch (RuntimeException e) {
            // Uma varredura que morre não avisa ninguém na próxima hora: engolir aqui é o que mantém o
            // agendador vivo. O erro fica no log, e o atraso continua sendo detectado na passagem seguinte.
            log.error("varredura de cadência falhou", e);
        }
    }
}
