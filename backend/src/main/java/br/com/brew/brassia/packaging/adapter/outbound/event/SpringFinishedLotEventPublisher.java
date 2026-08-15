package br.com.brew.brassia.packaging.adapter.outbound.event;

import br.com.brew.brassia.packaging.FinishedLotReleased;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringFinishedLotEventPublisher implements FinishedLotEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringFinishedLotEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(FinishedLotReleased event) {
        publisher.publishEvent(event);
    }
}
