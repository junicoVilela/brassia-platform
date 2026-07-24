package br.com.brew.brassia.referencedata.adapter.outbound.event;

import br.com.brew.brassia.referencedata.ReferenceDatasetPublished;
import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDataEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringReferenceDataEventPublisher implements ReferenceDataEventPublisher {
    private final ApplicationEventPublisher publisher;

    SpringReferenceDataEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(ReferenceDatasetPublished event) {
        publisher.publishEvent(event);
    }
}
