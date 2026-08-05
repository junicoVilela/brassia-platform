package br.com.brew.brassia.packaging.adapter.inbound.gateway;

import br.com.brew.brassia.traceability.LineageSource;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A ponta do envase na genealogia (TRC-001): lote → plano → execução → produto acabado → expedição.
 *
 * <p>A TRC-001-B trouxe o lote de produto acabado — o objeto que um recall recolhe. A TRC-001-D
 * fechou o passo seguinte: para onde ele foi. A lacuna que sobrou é por lote, e não da plataforma:
 * lote de produto acabado <em>sem expedição registrada</em> ainda não se sabe onde está, e é isso
 * que {@link #gapsOf} declara.
 */
@Component
class PackagingLineageAdapter implements LineageSource {

    private final JdbcClient jdbc;

    PackagingLineageAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        return switch (type) {
            case PACKAGING_PLAN -> jdbc
                    .sql("SELECT code FROM packaging_plan WHERE brewery_id = :brewery AND id = :id")
                    .param("brewery", breweryId).param("id", id)
                    .query(String.class).optional()
                    .map(code -> new Node(NodeType.PACKAGING_PLAN, id, code));
            case FINISHED_LOT -> jdbc
                    .sql("SELECT code FROM packaging_finished_lot WHERE brewery_id = :brewery AND id = :id")
                    .param("brewery", breweryId).param("id", id)
                    .query(String.class).optional()
                    .map(code -> new Node(NodeType.FINISHED_LOT, id, code));
            case SHIPMENT -> jdbc.sql("""
                    SELECT destination, units FROM packaging_shipment
                    WHERE brewery_id = :brewery AND id = :id
                    """)
                    .param("brewery", breweryId).param("id", id)
                    .query((rs, rowNum) -> new Node(NodeType.SHIPMENT, id,
                            "%s — %d un".formatted(rs.getString("destination"), rs.getInt("units"))))
                    .optional();
            case PACKAGING_RUN -> jdbc.sql("""
                    SELECT p.code, r.produced_units FROM packaging_run r
                    JOIN packaging_plan p ON p.id = r.plan_id
                    WHERE r.brewery_id = :brewery AND r.id = :id
                    """)
                    .param("brewery", breweryId).param("id", id)
                    .query((rs, rowNum) -> new Node(NodeType.PACKAGING_RUN, id,
                            "%s — %d un".formatted(rs.getString("code"), rs.getInt("produced_units"))))
                    .optional();
            default -> Optional.empty();
        };
    }

    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            case BATCH -> plansOfBatch(breweryId, node.id());
            case PACKAGING_PLAN -> runsOfPlan(breweryId, "r.plan_id = :id", node.id());
            case PACKAGING_RUN -> finishedLots(breweryId, "run_id = :id", node.id());
            case FINISHED_LOT -> shipments(breweryId, "finished_lot_id = :id", node.id());
            default -> List.of();
        };
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            case PACKAGING_PLAN -> plansOfPlan(breweryId, node.id());
            case PACKAGING_RUN -> runsOfPlan(breweryId, "r.id = :id", node.id());
            case FINISHED_LOT -> finishedLots(breweryId, "id = :id", node.id());
            case SHIPMENT -> shipments(breweryId, "id = :id", node.id());
            default -> List.of();
        };
    }

    /**
     * A lacuna do destino, agora por lote.
     *
     * <p>Antes da TRC-001-D ela era da plataforma: não existia expedição, e nenhum lote tinha
     * destino. Agora existe, e a lacuna passou a ser um fato sobre <em>este</em> lote — ele saiu da
     * linha e ninguém registrou para onde foi. É a diferença entre "o sistema não sabe registrar" e
     * "ninguém registrou", e só a segunda é acionável: num recall, é a caixa de cerveja que não se
     * sabe onde está.
     */
    @Override
    public List<Gap> gapsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.FINISHED_LOT) {
            return List.of();
        }
        var shipped = jdbc.sql("SELECT 1 FROM packaging_shipment "
                        + "WHERE brewery_id = :brewery AND finished_lot_id = :id LIMIT 1")
                .param("brewery", breweryId).param("id", node.id())
                .query(Integer.class).optional();
        if (shipped.isPresent()) {
            return List.of();
        }
        return List.of(new Gap(node, "expedição e destino",
                "este lote de produto acabado não tem expedição registrada: não se sabe para onde ele "
                        + "foi, nem a quem avisar num recall"));
    }

    /** Produto acabado → expedição (TRC-001-D): a metade de fora da fábrica. */
    private List<Edge> shipments(UUID breweryId, String filter, UUID id) {
        return jdbc.sql("""
                SELECT id, finished_lot_id, destination, units, shipped_on FROM packaging_shipment
                WHERE brewery_id = :brewery AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.FINISHED_LOT, rs.getObject("finished_lot_id", UUID.class)),
                        new Node(NodeType.SHIPMENT, rs.getObject("id", UUID.class),
                                "%s — %d un".formatted(rs.getString("destination"), rs.getInt("units"))),
                        "expedição", EdgeStrength.CONFIRMED,
                        rs.getDate("shipped_on").toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()))
                .list();
    }

    /** Execução → lote de produto acabado (TRC-001-B): um envase, um lote. */
    private List<Edge> finishedLots(UUID breweryId, String filter, UUID id) {
        return jdbc.sql("""
                SELECT id, code, run_id, packaged_on, units FROM packaging_finished_lot
                WHERE brewery_id = :brewery AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.PACKAGING_RUN, rs.getObject("run_id", UUID.class)),
                        new Node(NodeType.FINISHED_LOT, rs.getObject("id", UUID.class),
                                "%s — %d un".formatted(rs.getString("code"), rs.getInt("units"))),
                        "lote de produto acabado", EdgeStrength.CONFIRMED,
                        rs.getDate("packaged_on").toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()))
                .list();
    }

    private List<Edge> plansOfBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, code, batch_id, planned_start FROM packaging_plan
                WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> planEdge(rs.getObject("batch_id", UUID.class),
                        rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getTimestamp("planned_start").toInstant()))
                .list();
    }

    private List<Edge> plansOfPlan(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT id, code, batch_id, planned_start FROM packaging_plan
                WHERE brewery_id = :brewery AND id = :plan
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, rowNum) -> planEdge(rs.getObject("batch_id", UUID.class),
                        rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getTimestamp("planned_start").toInstant()))
                .list();
    }

    private static Edge planEdge(UUID batchId, UUID planId, String code, java.time.Instant at) {
        return new Edge(Node.of(NodeType.BATCH, batchId),
                new Node(NodeType.PACKAGING_PLAN, planId, code),
                "plano de envase", EdgeStrength.CONFIRMED, at);
    }

    private List<Edge> runsOfPlan(UUID breweryId, String filter, UUID id) {
        var edges = new ArrayList<Edge>();
        edges.addAll(jdbc.sql("""
                SELECT r.id, r.plan_id, r.produced_units, r.executed_at, p.code
                FROM packaging_run r JOIN packaging_plan p ON p.id = r.plan_id
                WHERE r.brewery_id = :brewery AND %s
                """.formatted(filter))
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.PACKAGING_PLAN, rs.getObject("plan_id", UUID.class)),
                        new Node(NodeType.PACKAGING_RUN, rs.getObject("id", UUID.class),
                                "%s — %d un".formatted(rs.getString("code"), rs.getInt("produced_units"))),
                        "envase executado", EdgeStrength.CONFIRMED,
                        rs.getTimestamp("executed_at").toInstant()))
                .list());
        return edges;
    }
}
