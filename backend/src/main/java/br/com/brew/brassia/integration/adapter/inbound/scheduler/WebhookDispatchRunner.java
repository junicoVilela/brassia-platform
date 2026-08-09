package br.com.brew.brassia.integration.adapter.inbound.scheduler;

import br.com.brew.brassia.integration.application.service.DeliveryDispatcher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispara a rodada de entregas (INT-002).
 *
 * <p><strong>A rodada inteira é uma transação, e é o travamento que exige isso.</strong> O
 * {@code FOR UPDATE SKIP LOCKED} do repositório segura as linhas escolhidas enquanto durar a transação de
 * quem chamou; sem uma transação em volta, o travamento acabaria no fim da consulta e outra instância
 * pegaria as mesmas entregas — o destino receberia o evento em duplicidade.
 *
 * <p>Não guarda "quando rodou por último". O estado de cada entrega já responde isso, e um marcador ao
 * lado seria uma segunda verdade que se perde numa restauração de backup.
 */
@Component
class WebhookDispatchRunner {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchRunner.class);

    private final DeliveryDispatcher dispatcher;

    WebhookDispatchRunner(DeliveryDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Scheduled(fixedDelayString = "${brassia.integration.dispatch-interval:PT15S}")
    @Transactional
    void run() {
        try {
            var attempted = dispatcher.dispatchDue();
            if (attempted > 0) {
                log.debug("webhook: {} entregas tentadas", attempted);
            }
        } catch (RuntimeException ex) {
            // O agendador não pode morrer: uma exceção não capturada em @Scheduled com fixedDelay para o
            // agendamento daquele método para sempre, e a fila deixaria de ser processada em silêncio.
            log.warn("rodada de webhooks falhou: {}", ex.getMessage());
        }
    }
}
