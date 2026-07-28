package br.com.brew.brassia.sanitation.adapter.outbound.event;

import br.com.brew.brassia.sanitation.CleaningCycleReleased;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringCleaningCycleEventPublisher implements CleaningCycleEventPublisher {
    private final ApplicationEventPublisher publisher;

    SpringCleaningCycleEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(CleaningCycleReleased event) {
        publisher.publishEvent(event);
    }
}
