package br.com.brew.brassia.fermentation.application.port.outbound;

import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import java.util.Optional;
import java.util.UUID;

public interface YeastPolicyRepository {

    /** Vazio quando a cervejaria ainda não configurou a política (vale o padrão do domínio). */
    Optional<YeastPolicy> find(UUID breweryId);

    void save(UUID breweryId, YeastPolicy policy);
}
