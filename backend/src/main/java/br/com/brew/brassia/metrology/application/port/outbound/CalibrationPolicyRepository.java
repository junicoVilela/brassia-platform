package br.com.brew.brassia.metrology.application.port.outbound;

import br.com.brew.brassia.metrology.domain.CalibrationPolicy;
import java.util.UUID;

public interface CalibrationPolicyRepository {

    /** Nunca vazio: sem linhas configuradas devolve política sem periodicidade nenhuma. */
    CalibrationPolicy find(UUID breweryId);

    void save(CalibrationPolicy policy);
}
