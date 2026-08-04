package br.com.brew.brassia.sensory.application.port.outbound;

import br.com.brew.brassia.sensory.domain.SensoryPolicy;
import java.util.UUID;

public interface SensoryPolicyRepository {

    /** Nunca vazio: sem configuração devolve a escala padrão de 10 pontos. */
    SensoryPolicy find(UUID breweryId);

    void save(SensoryPolicy policy);
}
