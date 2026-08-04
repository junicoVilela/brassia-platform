package br.com.brew.brassia.sensory.application.service;

import br.com.brew.brassia.sensory.application.port.inbound.SensoryQueries;
import br.com.brew.brassia.sensory.application.port.outbound.SensorySessionRepository;
import br.com.brew.brassia.sensory.domain.SensorySession;
import br.com.brew.brassia.sensory.domain.SessionResults;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas da análise sensorial.
 *
 * <p>O resultado é consolidado pelo agregado, que recusa a sessão ainda não encerrada — a regra da
 * cegueira mora no domínio, não numa checagem espalhada pela camada de consulta.
 */
public final class SensoryQueriesHandler implements SensoryQueries {

    private final SensorySessionRepository sessions;

    public SensoryQueriesHandler(SensorySessionRepository sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    @Override
    public List<SensorySession> sessions(UUID breweryId) {
        return sessions.findAll(breweryId);
    }

    @Override
    public Optional<SensorySession> session(UUID breweryId, UUID sessionId) {
        return sessions.findById(breweryId, sessionId);
    }

    @Override
    public int evaluationCount(UUID breweryId, UUID sessionId) {
        return sessions.countEvaluations(breweryId, sessionId);
    }

    @Override
    public SessionResults results(UUID breweryId, UUID sessionId) {
        var session = sessions.findById(breweryId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("sessão sensorial inexistente"));
        return session.results(sessions.findEvaluations(breweryId, sessionId));
    }
}
