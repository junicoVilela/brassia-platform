package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.Carbonation;
import java.util.Optional;
import java.util.UUID;

public interface CarbonationRepository {

    /** Grava a decisão do plano; recalcular substitui a decisão inteira. */
    void save(Carbonation carbonation);

    Optional<Carbonation> findByPlan(UUID breweryId, UUID planId);
}
