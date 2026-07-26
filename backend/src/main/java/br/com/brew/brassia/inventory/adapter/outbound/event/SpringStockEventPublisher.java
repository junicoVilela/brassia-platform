package br.com.brew.brassia.inventory.adapter.outbound.event;

import br.com.brew.brassia.inventory.StockReserved;
import br.com.brew.brassia.inventory.application.port.outbound.StockEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringStockEventPublisher implements StockEventPublisher {
    private final ApplicationEventPublisher publisher;

    SpringStockEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(StockReserved event) {
        publisher.publishEvent(event);
    }
}
