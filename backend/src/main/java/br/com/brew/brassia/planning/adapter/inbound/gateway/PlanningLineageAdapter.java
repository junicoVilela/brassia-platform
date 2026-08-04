package br.com.brew.brassia.planning.adapter.inbound.gateway;

import br.com.brew.brassia.traceability.LineageSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A OP na genealogia (TRC-001).
 *
 * <p>O planejamento não contribui aresta nenhuma: os dois elos da OP moram nas tabelas de quem os
 * registrou — a reserva no estoque, o lote na produção. O que ele tem é o nome: sem isto a ordem
 * apareceria no grafo como um id cru, e um grafo de ids não se lê.
 */
@Component
class PlanningLineageAdapter implements LineageSource {

    private final JdbcClient jdbc;

    PlanningLineageAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        if (type != NodeType.BREW_ORDER) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT code FROM brew_order WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query(String.class).optional()
                .map(code -> new Node(NodeType.BREW_ORDER, id, code));
    }

    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        return List.of();
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        return List.of();
    }
}
