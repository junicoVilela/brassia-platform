package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import java.math.BigDecimal;
import java.util.UUID;

/** Consulta e ajusta a política de reutilização da cervejaria (YST-002). */
public interface YeastPolicyUseCase {
    YeastPolicy get(UUID breweryId);

    void save(UUID actorId, UUID breweryId, Integer maxGeneration, Integer maxAgeDays, BigDecimal minViability);
}
