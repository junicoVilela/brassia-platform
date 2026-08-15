package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.FinishedLotReleased;

public interface FinishedLotEventPublisher {

    void publish(FinishedLotReleased event);
}
