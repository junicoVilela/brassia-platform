package br.com.brew.brassia.traceability.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.EdgeStrength;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propagação da contenção (FDS-002): até onde o bloqueio vai e com que força.
 *
 * <p>A decisão que estes testes fixam é a mais consequente da história: intenção propaga, mas
 * chega marcada como suspeita, e um caminho confirmado sempre vence um suspeito para o mesmo nó.
 */
class SpreadTest {

    private static final Instant T0 = Instant.parse("2026-08-05T10:00:00Z");

    private static final Node LOTE_INSUMO = node(NodeType.STOCK_LOT, "Malte L-22");
    private static final Node ORDEM = node(NodeType.BREW_ORDER, "OP-100");
    private static final Node LOTE = node(NodeType.BATCH, "LOTE-100");
    private static final Node PLANO = node(NodeType.PACKAGING_PLAN, "ENV-1");
    private static final Node ENVASE = node(NodeType.PACKAGING_RUN, "ENV-1 — 780 un");
    private static final Node ACABADO = node(NodeType.FINISHED_LOT, "LOTE-100/1");

    @Test
    @DisplayName("o bloqueio do lote alcança o envase e o produto acabado, todos confirmados")
    void alcancaDescendentesConfirmados() {
        var graph = chain();

        var spread = Spread.from(LOTE, Direction.FORWARD, 6, graph);

        assertThat(spread.affected()).extracting(affected -> affected.node().type())
                .containsExactlyInAnyOrder(NodeType.PACKAGING_PLAN, NodeType.PACKAGING_RUN,
                        NodeType.FINISHED_LOT);
        assertThat(spread.affected()).allMatch(affected -> !affected.suspected());
    }

    @Test
    @DisplayName("caminho por reserva alcança como suspeita — intenção não vira fato")
    void intencaoAlcancaComoSuspeita() {
        var graph = chain();

        // A reserva liga insumo → OP; tudo depois dela herda a suspeita.
        var spread = Spread.from(LOTE_INSUMO, Direction.FORWARD, 6, graph);

        assertThat(spread.reaching(ORDEM)).get()
                .satisfies(affected -> assertThat(affected.suspected()).isTrue());
        assertThat(spread.reaching(ACABADO)).get()
                .satisfies(affected -> assertThat(affected.suspected()).isTrue());
    }

    @Test
    @DisplayName("caminho confirmado vence o suspeito quando os dois chegam ao mesmo nó")
    void confirmadoVenceSuspeito() {
        var graph = chain()
                // Um segundo caminho, confirmado, do insumo direto ao lote.
                .edge(LOTE_INSUMO, LOTE, "consumo por lote", EdgeStrength.CONFIRMED);

        var spread = Spread.from(LOTE_INSUMO, Direction.FORWARD, 6, graph);

        assertThat(spread.reaching(LOTE)).get()
                .satisfies(affected -> assertThat(affected.suspected()).isFalse());
        // E o que vem depois do lote deixa de ser suposição junto.
        assertThat(spread.reaching(ENVASE)).get()
                .satisfies(affected -> assertThat(affected.suspected()).isFalse());
    }

    @Test
    @DisplayName("de trás para frente responde qual bloqueio alcança o plano — é a mesma travessia")
    void deTrasParaFrenteEncontraAOrigem() {
        var spread = Spread.from(PLANO, Direction.BACKWARD, 6, chain());

        assertThat(spread.reaching(LOTE)).isPresent();
        assertThat(spread.reaching(ORDEM)).isPresent();
        // O que veio depois do plano não é ancestral dele.
        assertThat(spread.reaching(ACABADO)).isEmpty();
    }

    @Test
    @DisplayName("o nó da própria origem é alcançado, e confirmado")
    void aOrigemAlcancaASiMesma() {
        var spread = Spread.from(LOTE, Direction.FORWARD, 6, chain());

        assertThat(spread.reaching(LOTE)).get()
                .satisfies(affected -> assertThat(affected.suspected()).isFalse());
        // Mas ela não aparece na lista de atingidos: quem foi bloqueado não é seu próprio efeito.
        assertThat(spread.affected()).noneMatch(affected -> affected.node().equals(LOTE));
    }

    @Test
    @DisplayName("corte de profundidade se declara: contenção parcial que parece completa é pior")
    void corteDeProfundidadeSeDeclara() {
        var spread = Spread.from(LOTE, Direction.FORWARD, 1, chain());

        assertThat(spread.truncated()).isTrue();
        assertThat(spread.affected()).extracting(affected -> affected.node().type())
                .containsExactly(NodeType.PACKAGING_PLAN);
    }

    @Test
    @DisplayName("ciclo não trava a travessia")
    void cicloNaoTrava() {
        var colheita = node(NodeType.YEAST_HARVEST, "COL-1");
        var proximoLote = node(NodeType.BATCH, "LOTE-101");
        var graph = new FakeGraph()
                .edge(LOTE, colheita, "coleta de levedura", EdgeStrength.CONFIRMED)
                .edge(colheita, proximoLote, "inoculação", EdgeStrength.CONFIRMED)
                .edge(proximoLote, colheita, "coleta de levedura", EdgeStrength.CONFIRMED);

        var spread = Spread.from(LOTE, Direction.FORWARD, 6, graph);

        assertThat(spread.affected()).hasSize(2);
    }

    /** Insumo -(reserva)-> OP -> lote -> plano -> envase -> produto acabado. */
    private static FakeGraph chain() {
        return new FakeGraph()
                .edge(LOTE_INSUMO, ORDEM, "reserva de insumo", EdgeStrength.INTENDED)
                .edge(ORDEM, LOTE, "ordem executada", EdgeStrength.CONFIRMED)
                .edge(LOTE, PLANO, "plano de envase", EdgeStrength.CONFIRMED)
                .edge(PLANO, ENVASE, "envase executado", EdgeStrength.CONFIRMED)
                .edge(ENVASE, ACABADO, "lote de produto acabado", EdgeStrength.CONFIRMED);
    }

    private static Node node(NodeType type, String label) {
        return new Node(type, UUID.nameUUIDFromBytes(label.getBytes()), label);
    }

    /** Grafo de mentira, montado aresta a aresta — o domínio não conhece banco nem módulo. */
    private static final class FakeGraph implements LineageGraph {

        private final List<Edge> edges = new ArrayList<>();

        FakeGraph edge(Node from, Node to, String kind, EdgeStrength strength) {
            edges.add(new Edge(from, to, kind, strength, T0.plusSeconds(edges.size())));
            return this;
        }

        @Override
        public List<Edge> edgesOf(Node node, Direction direction) {
            var found = new ArrayList<Edge>();
            for (Edge edge : edges) {
                if (direction.includesForward() && edge.from().equals(node)) {
                    found.add(edge);
                }
                if (direction.includesBackward() && edge.to().equals(node)) {
                    found.add(edge);
                }
            }
            return found;
        }

        @Override
        public List<Gap> gapsOf(Node node) {
            return List.of();
        }
    }
}
