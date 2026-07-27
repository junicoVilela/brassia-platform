package br.com.brew.brassia.production.adapter.outbound.event;

import br.com.brew.brassia.production.CorrectionApplied;
import br.com.brew.brassia.production.application.port.outbound.ProductionEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringProductionEventPublisher implements ProductionEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringProductionEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(CorrectionApplied event) {
        publisher.publishEvent(event);
    }
}
