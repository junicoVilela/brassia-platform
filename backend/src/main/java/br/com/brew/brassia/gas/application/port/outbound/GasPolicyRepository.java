package br.com.brew.brassia.gas.application.port.outbound;

import br.com.brew.brassia.gas.domain.GasPolicy;
import java.util.UUID;

public interface GasPolicyRepository {

    /** Nunca vazio: cervejaria sem linha configurada devolve política sem periodicidade. */
    GasPolicy find(UUID breweryId);

    void save(GasPolicy policy);
}
