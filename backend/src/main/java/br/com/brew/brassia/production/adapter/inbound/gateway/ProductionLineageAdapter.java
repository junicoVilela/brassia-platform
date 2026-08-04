package br.com.brew.brassia.production.adapter.inbound.gateway;

import br.com.brew.brassia.traceability.LineageSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Elo OP → lote (TRC-001), respondido pela produção porque é dela a tabela que o guarda.
 *
 * <p>É o elo mais forte da cadeia: {@code UNIQUE (brewery_id, order_id)} garante uma OP por lote e
 * um lote por OP, então aqui não há ambiguidade a resolver nem escolha a fazer.
 */
@Component
class ProductionLineageAdapter implements LineageSource {

    private final JdbcClient jdbc;

    ProductionLineageAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        if (type != NodeType.BATCH) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT code FROM production_batch WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query(String.class).optional()
                .map(code -> new Node(NodeType.BATCH, id, code));
    }

    /** Da OP nasce o lote. Do lote não nasce nada que a produção conheça. */
    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BREW_ORDER) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT id, code, order_id, started_at FROM production_batch
                WHERE brewery_id = :brewery AND order_id = :order
                """)
                .param("brewery", breweryId).param("order", node.id())
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.BREW_ORDER, rs.getObject("order_id", UUID.class)),
                        new Node(NodeType.BATCH, rs.getObject("id", UUID.class), rs.getString("code")),
                        "ordem executada", EdgeStrength.CONFIRMED,
                        rs.getTimestamp("started_at").toInstant()))
                .list();
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BATCH) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT id, code, order_id, started_at FROM production_batch
                WHERE brewery_id = :brewery AND id = :batch
                """)
                .param("brewery", breweryId).param("batch", node.id())
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.BREW_ORDER, rs.getObject("order_id", UUID.class)),
                        new Node(NodeType.BATCH, rs.getObject("id", UUID.class), rs.getString("code")),
                        "ordem executada", EdgeStrength.CONFIRMED,
                        rs.getTimestamp("started_at").toInstant()))
                .list();
    }

    /**
     * A lacuna do blend (TRC-001-A).
     *
     * <p>A história pede a cadeia "insumo, OP, lote, blend e embalagem", mas a plataforma não tem
     * nenhum conceito de misturar lotes: não há tabela, agregado nem comando. Declarar a ausência
     * é mais honesto do que inventar um modelo de blend para preencher o desenho — e é o que o
     * critério "ausência de elo é evidenciada" pede.
     */
    @Override
    public List<Gap> gapsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BATCH) {
            return List.of();
        }
        return List.of(new Gap(node, "blend de lotes",
                "a plataforma não registra mistura de lotes; a cadeia vai do lote direto ao envase (TRC-001-A)"));
    }
}
