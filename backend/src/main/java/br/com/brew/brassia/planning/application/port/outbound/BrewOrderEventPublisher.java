package br.com.brew.brassia.planning.application.port.outbound;

import br.com.brew.brassia.planning.BrewOrderReleased;

/** Publica eventos de domínio da ordem de produção para outros módulos. */
public interface BrewOrderEventPublisher {
    void publish(BrewOrderReleased event);
}
