package br.com.brew.brassia.sensory.application.port.outbound;

import br.com.brew.brassia.sensory.domain.SensoryEvaluation;
import br.com.brew.brassia.sensory.domain.SensorySession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SensorySessionRepository {

    void insert(SensorySession session);

    void update(SensorySession session);

    Optional<SensorySession> findById(UUID breweryId, UUID sessionId);

    Optional<SensorySession> lockById(UUID breweryId, UUID sessionId);

    List<SensorySession> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);

    /** Ficha só entra — nunca é atualizada. */
    void insertEvaluation(SensoryEvaluation evaluation);

    List<SensoryEvaluation> findEvaluations(UUID breweryId, UUID sessionId);

    boolean hasEvaluated(UUID breweryId, UUID sampleId, UUID tasterId);

    int countEvaluations(UUID breweryId, UUID sessionId);
}
