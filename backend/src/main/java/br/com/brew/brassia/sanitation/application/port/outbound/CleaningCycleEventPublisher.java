package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.CleaningCycleReleased;

/** Publica eventos de domínio do ciclo de limpeza para outros módulos (CLN-004). */
public interface CleaningCycleEventPublisher {
    void publish(CleaningCycleReleased event);
}
