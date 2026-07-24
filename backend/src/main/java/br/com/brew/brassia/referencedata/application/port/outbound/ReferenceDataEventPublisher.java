package br.com.brew.brassia.referencedata.application.port.outbound;

import br.com.brew.brassia.referencedata.ReferenceDatasetPublished;

/** Publica eventos de domínio de dados de referência para outros módulos. */
public interface ReferenceDataEventPublisher {
    void publish(ReferenceDatasetPublished event);
}
