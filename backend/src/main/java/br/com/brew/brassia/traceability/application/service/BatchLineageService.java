package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.BatchLineageLookup;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * As duas pontas da genealogia do lote, para o relatório (RPT-001).
 *
 * <p>Duas travessias, e não uma: subir e descer respondem perguntas diferentes — "de que insumos
 * este lote é feito" e "para onde esta cerveja foi". Juntá-las num grafo só devolveria a topologia
 * inteira, que é justamente o que o relatório não quer.
 *
 * <p>Lote que a genealogia não conhece devolve resumo vazio em vez de erro: o relatório sabe dizer
 * "sem genealogia" melhor do que uma exceção sabe.
 */
public final class BatchLineageService implements BatchLineageLookup {

    private final TraceabilityQueries queries;

    public BatchLineageService(TraceabilityQueries queries) {
        this.queries = Objects.requireNonNull(queries);
    }

    @Override
    public BatchLineage ofBatch(UUID breweryId, UUID batchId) {
        var up = walk(breweryId, batchId, Direction.BACKWARD);
        var down = walk(breweryId, batchId, Direction.FORWARD);
        if (up == null && down == null) {
            return BatchLineage.empty();
        }

        var gaps = new ArrayList<String>();
        collectGaps(up, gaps);
        collectGaps(down, gaps);
        return new BatchLineage(entries(up, batchId), entries(down, batchId), gaps,
                truncated(up) || truncated(down));
    }

    private Genealogy walk(UUID breweryId, UUID batchId, Direction direction) {
        try {
            return queries.genealogy(breweryId, NodeType.BATCH, batchId, direction, Genealogy.MAX_DEPTH);
        } catch (UnknownNodeException ex) {
            return null;
        }
    }

    /** Os nós alcançados, menos o próprio lote: ele é a pergunta, não a resposta. */
    private static List<LineageEntry> entries(Genealogy genealogy, UUID batchId) {
        if (genealogy == null) {
            return List.of();
        }
        var entries = new ArrayList<LineageEntry>();
        for (Node node : genealogy.nodes()) {
            if (node.type() == NodeType.BATCH && node.id().equals(batchId)) {
                continue;
            }
            entries.add(new LineageEntry(node.type().name(),
                    node.label() == null ? node.id().toString() : node.label()));
        }
        return entries;
    }

    private static void collectGaps(Genealogy genealogy, List<String> gaps) {
        if (genealogy == null) {
            return;
        }
        for (var gap : genealogy.gaps()) {
            gaps.add(gap.expectedLink() + ": " + gap.reason());
        }
    }

    private static boolean truncated(Genealogy genealogy) {
        return genealogy != null && genealogy.truncated();
    }
}
