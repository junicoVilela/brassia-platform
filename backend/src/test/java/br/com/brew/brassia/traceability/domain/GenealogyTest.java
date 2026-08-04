package br.com.brew.brassia.traceability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.EdgeStrength;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Travessia da genealogia: sentido, corte, ciclo e evidência de lacuna. */
class GenealogyTest {

    private static final Instant T0 = Instant.parse("2026-08-04T10:00:00Z");

    /** Grafo de mentira, montado aresta a aresta — o domínio não conhece banco nem módulo. */
    private static final class FakeGraph implements LineageGraph {

        private final List<Edge> edges = new ArrayList<>();
        private final Map<Node, List<Gap>> gaps = new HashMap<>();

        FakeGraph edge(Node from, Node to, String kind, EdgeStrength strength) {
            edges.add(new Edge(from, to, kind, strength, T0.plusSeconds(edges.size())));
            return this;
        }

        FakeGraph gap(Node node, String expected, String reason) {
            gaps.computeIfAbsent(node, key -> new ArrayList<>()).add(new Gap(node, expected, reason));
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
            return gaps.getOrDefault(node, List.of());
        }
    }

    private static Node node(NodeType type, String label) {
        return new Node(type, UUID.randomUUID(), label);
    }

    @Test
    void percorreParaFrenteDoInsumoAteOEnvase() {
        var lot = node(NodeType.STOCK_LOT, "Malte Pilsen L-22");
        var order = node(NodeType.BREW_ORDER, "OP-100");
        var batch = node(NodeType.BATCH, "LOTE-100");
        var plan = node(NodeType.PACKAGING_PLAN, "ENV-100");
        var graph = new FakeGraph()
                .edge(lot, order, "reserva de insumo", EdgeStrength.INTENDED)
                .edge(order, batch, "ordem executada", EdgeStrength.CONFIRMED)
                .edge(batch, plan, "plano de envase", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(lot, Direction.FORWARD, 5, graph);

        assertThat(genealogy.nodes()).containsExactlyInAnyOrder(lot, order, batch, plan);
        assertThat(genealogy.edges()).hasSize(3);
        assertThat(genealogy.truncated()).isFalse();
        assertThat(genealogy.reach()).isEqualTo(3);
    }

    @Test
    void paraTrasNaoTrazDescendentes() {
        var lot = node(NodeType.STOCK_LOT, "Malte");
        var order = node(NodeType.BREW_ORDER, "OP-100");
        var batch = node(NodeType.BATCH, "LOTE-100");
        var graph = new FakeGraph()
                .edge(lot, order, "reserva de insumo", EdgeStrength.INTENDED)
                .edge(order, batch, "ordem executada", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(batch, Direction.BACKWARD, 5, graph);

        assertThat(genealogy.nodes()).containsExactlyInAnyOrder(batch, order, lot);
        // Partindo do lote de produção para trás, o plano de envase não deve aparecer nem se existir.
        assertThat(genealogy.nodes()).noneMatch(n -> n.type() == NodeType.PACKAGING_PLAN);
    }

    @Test
    void cortaNaProfundidadePedidaEDeclaraQueHaMais() {
        var lot = node(NodeType.STOCK_LOT, "Malte");
        var order = node(NodeType.BREW_ORDER, "OP-100");
        var batch = node(NodeType.BATCH, "LOTE-100");
        var graph = new FakeGraph()
                .edge(lot, order, "reserva de insumo", EdgeStrength.INTENDED)
                .edge(order, batch, "ordem executada", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(lot, Direction.FORWARD, 1, graph);

        assertThat(genealogy.nodes()).containsExactlyInAnyOrder(lot, order);
        // O corte não pode passar despercebido: um recall "completo" pela metade é pior que nenhum.
        assertThat(genealogy.truncated()).isTrue();
    }

    @Test
    void cicloDeLeveduraNaoFazATravessiaGirar() {
        var batchA = node(NodeType.BATCH, "LOTE-1");
        var harvest = node(NodeType.YEAST_HARVEST, "LEV-1 G2");
        var batchB = node(NodeType.BATCH, "LOTE-2");
        var graph = new FakeGraph()
                .edge(batchA, harvest, "coleta de levedura", EdgeStrength.CONFIRMED)
                .edge(harvest, batchB, "inoculação", EdgeStrength.CONFIRMED)
                // A volta fecha o ciclo: o lote 2 devolve levedura para a mesma coleta.
                .edge(batchB, harvest, "coleta de levedura", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(batchA, Direction.BOTH, Genealogy.MAX_DEPTH, graph);

        assertThat(genealogy.nodes()).containsExactlyInAnyOrder(batchA, harvest, batchB);
        assertThat(genealogy.edges()).hasSize(3);
    }

    @Test
    void lacunaDoNoAlcancadoEntraNoResultado() {
        var batch = node(NodeType.BATCH, "LOTE-100");
        var run = node(NodeType.PACKAGING_RUN, "Envase 12/03");
        var graph = new FakeGraph()
                .edge(batch, run, "envase executado", EdgeStrength.CONFIRMED)
                .gap(run, "lote de produto acabado", "o envase não gera lote de estoque (TRC-001-B)");

        var genealogy = Genealogy.walk(batch, Direction.FORWARD, 3, graph);

        assertThat(genealogy.gaps()).singleElement().satisfies(gap -> {
            assertThat(gap.from()).isEqualTo(run);
            assertThat(gap.reason()).contains("TRC-001-B");
        });
    }

    @Test
    void distingueIntencaoDeFato() {
        var lot = node(NodeType.STOCK_LOT, "Malte");
        var order = node(NodeType.BREW_ORDER, "OP-100");
        var batch = node(NodeType.BATCH, "LOTE-100");
        var graph = new FakeGraph()
                .edge(lot, order, "reserva de insumo", EdgeStrength.INTENDED)
                .edge(order, batch, "ordem executada", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(lot, Direction.FORWARD, 3, graph);

        assertThat(genealogy.intendedEdges()).singleElement()
                .satisfies(edge -> assertThat(edge.kind()).isEqualTo("reserva de insumo"));
    }

    @Test
    void noSemArestaFicaIsolado() {
        var batch = node(NodeType.BATCH, "LOTE-ORFAO");

        var genealogy = Genealogy.walk(batch, Direction.BOTH, 3, new FakeGraph());

        assertThat(genealogy.isolated()).isTrue();
        assertThat(genealogy.nodes()).containsExactly(batch);
        assertThat(genealogy.reach()).isZero();
    }

    @Test
    void oMesmoNoDescobertoPorDoisCaminhosEhUmSo() {
        var id = UUID.randomUUID();
        var comRotulo = new Node(NodeType.BATCH, id, "LOTE-100");
        var semRotulo = Node.of(NodeType.BATCH, id);
        var order = node(NodeType.BREW_ORDER, "OP-100");
        var plan = node(NodeType.PACKAGING_PLAN, "ENV-100");
        var graph = new FakeGraph()
                .edge(order, comRotulo, "ordem executada", EdgeStrength.CONFIRMED)
                .edge(semRotulo, plan, "plano de envase", EdgeStrength.CONFIRMED);

        var genealogy = Genealogy.walk(order, Direction.FORWARD, 3, graph);

        assertThat(genealogy.nodes()).hasSize(3);
        // E o rótulo conhecido prevalece, venha ele de qual provedor vier.
        assertThat(genealogy.nodes()).filteredOn(n -> n.type() == NodeType.BATCH)
                .singleElement().satisfies(n -> assertThat(n.label()).isEqualTo("LOTE-100"));
    }

    @Test
    void profundidadeAcimaDoTetoEhRecusada() {
        var batch = node(NodeType.BATCH, "LOTE-100");

        assertThatThrownBy(() -> Genealogy.walk(batch, Direction.BOTH, Genealogy.MAX_DEPTH + 1, new FakeGraph()))
                .isInstanceOf(DepthExceededException.class)
                .hasMessageContaining("máximo");
    }

    @Test
    void profundidadeZeroEhRecusada() {
        var batch = node(NodeType.BATCH, "LOTE-100");

        assertThatThrownBy(() -> Genealogy.walk(batch, Direction.BOTH, 0, new FakeGraph()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
