package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.domain.RecallDrill;
import java.util.List;
import java.util.UUID;

/** Leituras do simulado de recall (FDS-004). */
public interface DrillQueries {

    List<RecallDrill> list(UUID breweryId);

    /**
     * O relatório do simulado.
     *
     * <p>Enquanto o simulado corre, os números vêm do grafo — é o alvo que a equipe está tentando
     * localizar. Depois de encerrado, vêm congelados do próprio simulado: aquele foi o resultado
     * daquele dia, e recalculá-lo responderia sobre outro.
     *
     * @throws br.com.brew.brassia.traceability.domain.UnknownDrillException id inexistente
     */
    Report report(UUID breweryId, UUID drillId, int depth);

    /**
     * @param destinations destinos alcançados, com contato — a lista de chamada do exercício
     * @param gaps         lotes do escopo sem expedição registrada: o que nem o simulado alcança
     * @param findings      lacunas viradas do avesso: cada uma é uma ação corretiva sugerida
     */
    record Report(RecallDrill drill, int unitsInScope, int destinationsReached, List<Destination> destinations,
            List<Gap> gaps, List<String> findings, long elapsedSeconds) {}

    record Destination(UUID reference, String label, String contact, int units) {}
}
