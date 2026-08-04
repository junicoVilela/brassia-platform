package br.com.brew.brassia.fermentation.adapter.inbound.gateway;

import br.com.brew.brassia.traceability.LineageSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A levedura na genealogia (TRC-001) — o único elo que liga lote a lote.
 *
 * <p>Coletar levedura de um lote e inoculá-la em outro cria uma linhagem que atravessa brassagens:
 * uma contaminação na geração 3 explica um defeito na 5. É também a única parte do grafo que forma
 * ciclo — lote gera coleta que gera lote que gera coleta —, e é por isso que a travessia precisa de
 * conjunto de visitados em vez de recursão ingênua.
 */
@Component
class FermentationLineageAdapter implements LineageSource {

    private final JdbcClient jdbc;

    FermentationLineageAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        if (type != NodeType.YEAST_HARVEST) {
            return Optional.empty();
        }
        return jdbc.sql("""
                SELECT code, generation FROM fermentation_yeast_harvest
                WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Node(NodeType.YEAST_HARVEST, id,
                        "%s (G%d)".formatted(rs.getString("code"), rs.getInt("generation"))))
                .optional();
    }

    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            // Do lote sai a coleta.
            case BATCH -> harvests(breweryId, "source_batch_id = :id", node.id());
            // Da coleta sai o lote inoculado, e a coleta-filha da próxima geração.
            case YEAST_HARVEST -> {
                var edges = new ArrayList<Edge>(pitches(breweryId, "id = :id", node.id()));
                edges.addAll(generations(breweryId, "parent_harvest_id = :id", node.id()));
                yield edges;
            }
            default -> List.of();
        };
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            // Para trás, o lote veio da coleta que nele foi inoculada.
            case BATCH -> pitches(breweryId, "pitched_batch_id = :id", node.id());
            case YEAST_HARVEST -> {
                var edges = new ArrayList<Edge>(harvests(breweryId, "id = :id", node.id()));
                edges.addAll(generations(breweryId, "id = :id", node.id()));
                yield edges;
            }
            default -> List.of();
        };
    }

    /** Lote → coleta. */
    private List<Edge> harvests(UUID breweryId, String filter, UUID id) {
        return jdbc.sql("""
                SELECT id, code, generation, source_batch_id, harvested_at
                FROM fermentation_yeast_harvest
                WHERE brewery_id = :brewery AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.BATCH, rs.getObject("source_batch_id", UUID.class)),
                        harvestNode(rs.getObject("id", UUID.class), rs.getString("code"),
                                rs.getInt("generation")),
                        "coleta de levedura", EdgeStrength.CONFIRMED,
                        rs.getTimestamp("harvested_at").toInstant()))
                .list();
    }

    /** Coleta → lote inoculado. Só existe depois do uso confirmado. */
    private List<Edge> pitches(UUID breweryId, String filter, UUID id) {
        return jdbc.sql("""
                SELECT id, code, generation, pitched_batch_id, pitched_at
                FROM fermentation_yeast_harvest
                WHERE brewery_id = :brewery AND pitched_batch_id IS NOT NULL AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        harvestNode(rs.getObject("id", UUID.class), rs.getString("code"),
                                rs.getInt("generation")),
                        Node.of(NodeType.BATCH, rs.getObject("pitched_batch_id", UUID.class)),
                        "inoculação", EdgeStrength.CONFIRMED, pitchedAt(rs.getTimestamp("pitched_at"))))
                .list();
    }

    /** Coleta-mãe → coleta-filha: a geração seguinte da mesma linhagem. */
    private List<Edge> generations(UUID breweryId, String filter, UUID id) {
        return jdbc.sql("""
                SELECT id, code, generation, parent_harvest_id, harvested_at
                FROM fermentation_yeast_harvest
                WHERE brewery_id = :brewery AND parent_harvest_id IS NOT NULL AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.YEAST_HARVEST, rs.getObject("parent_harvest_id", UUID.class)),
                        harvestNode(rs.getObject("id", UUID.class), rs.getString("code"),
                                rs.getInt("generation")),
                        "geração seguinte", EdgeStrength.CONFIRMED,
                        rs.getTimestamp("harvested_at").toInstant()))
                .list();
    }

    private static Node harvestNode(UUID id, String code, int generation) {
        return new Node(NodeType.YEAST_HARVEST, id, "%s (G%d)".formatted(code, generation));
    }

    private static Instant pitchedAt(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
