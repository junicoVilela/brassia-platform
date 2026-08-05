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
 * A ponta do envase na genealogia (TRC-001): lote → plano → execução.
 *
 * <p>Desde a TRC-001-B a cadeia segue até o lote de produto acabado — o objeto que um recall
 * recolhe. O que ainda falta é o passo seguinte: para onde esse lote foi. Sem expedição não há
 * destino, e sem destino não há a quem avisar; é a lacuna TRC-001-D, declarada em {@link #gapsOf}.
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
            default -> List.of();
        };
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            case PACKAGING_PLAN -> plansOfPlan(breweryId, node.id());
            case PACKAGING_RUN -> runsOfPlan(breweryId, "r.id = :id", node.id());
            case FINISHED_LOT -> finishedLots(breweryId, "id = :id", node.id());
            default -> List.of();
        };
    }

    /**
     * A lacuna do destino (TRC-001-D).
     *
     * <p>Declarada no lote de produto acabado porque é o último nó que existe. A TRC-001-B fechou a
     * metade de dentro da fábrica: o lote agora existe e tem identidade. A metade de fora continua
     * aberta — não há expedição, então não se sabe para quem a cerveja foi, e é disso que a FDS-003
     * vai precisar para dizer a quem o recall se dirige.
     */
    @Override
    public List<Gap> gapsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.FINISHED_LOT) {
            return List.of();
        }
        return List.of(new Gap(node, "expedição e destino",
                "o lote de produto acabado existe, mas a plataforma não registra para onde ele foi: "
                        + "sem expedição não há destino nem a quem avisar num recall (TRC-001-D)"));
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
