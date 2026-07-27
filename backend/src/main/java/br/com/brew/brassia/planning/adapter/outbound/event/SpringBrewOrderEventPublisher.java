package br.com.brew.brassia.planning.adapter.outbound.event;

import br.com.brew.brassia.planning.BrewOrderCancelled;
import br.com.brew.brassia.planning.BrewOrderReleased;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringBrewOrderEventPublisher implements BrewOrderEventPublisher {
    private final ApplicationEventPublisher publisher;

    SpringBrewOrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(BrewOrderReleased event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(BrewOrderCancelled event) {
        publisher.publishEvent(event);
    }
}
