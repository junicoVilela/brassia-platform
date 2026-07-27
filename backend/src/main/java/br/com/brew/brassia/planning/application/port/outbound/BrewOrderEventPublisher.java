package br.com.brew.brassia.planning.application.port.outbound;

import br.com.brew.brassia.planning.BrewOrderCancelled;
import br.com.brew.brassia.planning.BrewOrderReleased;
import br.com.brew.brassia.planning.BrewOrderStarted;

/** Publica eventos de domínio da ordem de produção para outros módulos. */
public interface BrewOrderEventPublisher {
    void publish(BrewOrderReleased event);

    void publish(BrewOrderCancelled event);

    void publish(BrewOrderStarted event);
}
