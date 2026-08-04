package br.com.brew.brassia.quality.application.port.outbound;

import br.com.brew.brassia.quality.domain.CapaPolicy;
import java.util.UUID;

public interface CapaPolicyRepository {

    /** Nunca vazio: sem linhas configuradas devolve política sem prazos. */
    CapaPolicy find(UUID breweryId);

    void save(CapaPolicy policy);
}
