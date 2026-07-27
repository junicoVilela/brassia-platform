package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.CorrectionApplied;

/** Publica eventos de domínio da produção para outros módulos. */
public interface ProductionEventPublisher {
    void publish(CorrectionApplied event);
}
