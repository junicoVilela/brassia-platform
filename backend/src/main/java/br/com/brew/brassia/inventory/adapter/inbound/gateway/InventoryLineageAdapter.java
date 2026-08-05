package br.com.brew.brassia.inventory.adapter.inbound.gateway;

import br.com.brew.brassia.traceability.LineageSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O estoque na genealogia (TRC-001): de onde o insumo saiu e para onde foi.
 *
 * <p>As arestas daqui não valem todas o mesmo, e o grafo diz isso em vez de disfarçar:
 *
 * <ul>
 *   <li><strong>Reserva → OP é intenção.</strong> A reserva registra qual lote foi separado para a
 *       ordem, não qual foi de fato ao moinho. Num recall, tratar isso como fato é recolher o lote
 *       errado.</li>
 *   <li><strong>Consumo → OP é fato</strong> desde a TRC-001-C: quem brassou confirmou, lote a
 *       lote, o que entrou. Quando o consumo existe, a reserva da mesma OP <em>não</em> vira
 *       aresta: ela foi liberada no ato, e mostrar as duas contaria o mesmo malte duas vezes.</li>
 *   <li><strong>Consumo → plano de envase é fato.</strong> A embalagem que virou lata cheia saiu do
 *       estoque de verdade, com movimento de consumo apontando para o plano.</li>
 * </ul>
 *
 * <p>Reserva liberada não vira aresta: o saldo reservado por referência é somado (RESERVATION −
 * RELEASE) e só sobrevive o que ainda está preso à ordem. Uma OP cancelada não deixa rastro falso.
 */
@Component
class InventoryLineageAdapter implements LineageSource {

    private static final String RESERVED_BY_REFERENCE = """
            SELECT lot_id, reference, MIN(occurred_at) AS first_at,
                   SUM(CASE WHEN type = 'RESERVATION' THEN quantity ELSE -quantity END) AS net
            FROM stock_movement
            WHERE brewery_id = :brewery AND reference IS NOT NULL AND type IN ('RESERVATION', 'RELEASE')
              AND %s
            GROUP BY lot_id, reference
            HAVING SUM(CASE WHEN type = 'RESERVATION' THEN quantity ELSE -quantity END) > 0
            """;

    /**
     * {@code reason = 'envase'} é o que distingue o consumo do envase de qualquer outro.
     *
     * <p>A coluna {@code reference} do ledger é um UUID sem tipo: ela aponta para uma OP quando é
     * reserva e para um plano quando é consumo do envase, e nada no banco diz qual. Sem o filtro,
     * um consumo lançado à mão com referência qualquer viraria no grafo um "plano de envase" que
     * não existe — uma aresta inventada, que é pior do que uma aresta faltando.
     */
    private static final String CONSUMED_BY_REFERENCE = """
            SELECT lot_id, reference, MIN(occurred_at) AS first_at
            FROM stock_movement
            WHERE brewery_id = :brewery AND reference IS NOT NULL AND type = 'CONSUMPTION'
              AND reason = :reason AND %s
            GROUP BY lot_id, reference
            """;

    private final JdbcClient jdbc;

    InventoryLineageAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        if (type != NodeType.STOCK_LOT) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT supplier_lot_code FROM stock_lot WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query((rs, rowNum) -> new Node(NodeType.STOCK_LOT, id, label(rs.getString(1), id)))
                .optional();
    }

    /** O lote do fornecedor quando existe; senão o id curto, que ao menos identifica na tela. */
    private static String label(String supplierLotCode, UUID id) {
        return supplierLotCode != null && !supplierLotCode.isBlank()
                ? supplierLotCode
                : "lote " + id.toString().substring(0, 8);
    }

    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.STOCK_LOT) {
            return List.of();
        }
        var edges = new ArrayList<Edge>();
        edges.addAll(reservations(breweryId, "lot_id = :lot", "lot", node.id()));
        edges.addAll(brewConsumptions(breweryId, "lot_id = :lot", "lot", node.id()));
        edges.addAll(packagingConsumptions(breweryId, "lot_id = :lot", "lot", node.id()));
        return edges;
    }

    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        return switch (node.type()) {
            // Consumo confirmado substitui a reserva: as duas juntas contariam o mesmo malte duas
            // vezes, uma como intenção e outra como fato.
            case BREW_ORDER -> {
                var consumed = brewConsumptions(breweryId, "reference = :ref", "ref", node.id());
                yield consumed.isEmpty()
                        ? reservations(breweryId, "reference = :ref", "ref", node.id())
                        : consumed;
            }
            case PACKAGING_PLAN -> packagingConsumptions(breweryId, "reference = :ref", "ref", node.id());
            default -> List.of();
        };
    }

    /**
     * A lacuna do consumo, agora por OP.
     *
     * <p>Antes da TRC-001-C ela era da plataforma: não existia comando de consumo, e nenhuma ordem
     * podia tê-lo. Agora existe, e a lacuna passou a ser um fato sobre <em>esta</em> ordem — quem
     * brassou ainda não confirmou o que usou, e o elo para trás continua sendo a reserva, que é
     * intenção. É a diferença entre "o sistema não sabe registrar" e "ninguém registrou".
     */
    @Override
    public List<Gap> gapsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BREW_ORDER) {
            return List.of();
        }
        if (!brewConsumptions(breweryId, "reference = :ref", "ref", node.id()).isEmpty()) {
            return List.of();
        }
        return List.of(new Gap(node, "consumo de insumo por lote",
                "esta ordem ainda não teve o consumo do dia de brassa confirmado; o elo para trás é "
                        + "a reserva, que é intenção e não fato"));
    }

    private List<Edge> reservations(UUID breweryId, String filter, String param, UUID value) {
        return jdbc.sql(RESERVED_BY_REFERENCE.formatted(filter))
                .param("brewery", breweryId).param(param, value)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.STOCK_LOT, rs.getObject("lot_id", UUID.class)),
                        Node.of(NodeType.BREW_ORDER, rs.getObject("reference", UUID.class)),
                        "reserva de insumo", EdgeStrength.INTENDED,
                        rs.getTimestamp("first_at").toInstant()))
                .list();
    }

    /** Consumo do dia de brassa (TRC-001-C): insumo → OP, e é fato. */
    private List<Edge> brewConsumptions(UUID breweryId, String filter, String param, UUID value) {
        return consumptions(breweryId, filter, param, value, "brassagem", NodeType.BREW_ORDER,
                "consumo de insumo");
    }

    private List<Edge> packagingConsumptions(UUID breweryId, String filter, String param, UUID value) {
        return consumptions(breweryId, filter, param, value, "envase", NodeType.PACKAGING_PLAN,
                "consumo de embalagem");
    }

    private List<Edge> consumptions(UUID breweryId, String filter, String param, UUID value, String reason,
            NodeType target, String kind) {
        return jdbc.sql(CONSUMED_BY_REFERENCE.formatted(filter))
                .param("brewery", breweryId).param(param, value).param("reason", reason)
                .query((rs, rowNum) -> new Edge(
                        Node.of(NodeType.STOCK_LOT, rs.getObject("lot_id", UUID.class)),
                        Node.of(target, rs.getObject("reference", UUID.class)),
                        kind, EdgeStrength.CONFIRMED,
                        rs.getTimestamp("first_at").toInstant()))
                .list();
    }
}
