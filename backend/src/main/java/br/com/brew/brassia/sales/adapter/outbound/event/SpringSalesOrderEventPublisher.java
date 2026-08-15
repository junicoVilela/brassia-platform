package br.com.brew.brassia.sales.adapter.outbound.event;

import br.com.brew.brassia.sales.SalesOrderCancelled;
import br.com.brew.brassia.sales.SalesOrderFulfilled;
import br.com.brew.brassia.sales.SalesOrderPlaced;
import br.com.brew.brassia.sales.application.port.outbound.SalesOrderEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringSalesOrderEventPublisher implements SalesOrderEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringSalesOrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(SalesOrderPlaced event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(SalesOrderCancelled event) {
        publisher.publishEvent(event);
    }

    @Override
    public void publish(SalesOrderFulfilled event) {
        publisher.publishEvent(event);
    }
}
