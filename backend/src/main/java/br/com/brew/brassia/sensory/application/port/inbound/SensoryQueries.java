package br.com.brew.brassia.sensory.application.port.inbound;

import br.com.brew.brassia.sensory.domain.SensorySession;
import br.com.brew.brassia.sensory.domain.SessionResults;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas da análise sensorial (SEN-001). */
public interface SensoryQueries {

    List<SensorySession> sessions(UUID breweryId);

    Optional<SensorySession> session(UUID breweryId, UUID sessionId);

    /** Quantas fichas já entraram — o único número público enquanto a sessão está aberta. */
    int evaluationCount(UUID breweryId, UUID sessionId);

    /**
     * Resultado consolidado. Lança {@code ResultsNotAvailableException} se a sessão ainda não foi
     * encerrada: é o critério da história.
     */
    SessionResults results(UUID breweryId, UUID sessionId);
}
