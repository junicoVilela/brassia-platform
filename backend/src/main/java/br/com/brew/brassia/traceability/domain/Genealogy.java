package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.EdgeStrength;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Genealogia de um nó: os nós alcançados, as arestas que os ligam e os elos que faltam (TRC-001).
 *
 * <p>É derivada, nunca armazenada. Guardar o grafo criaria uma segunda verdade, que envelheceria
 * no instante seguinte a qualquer envase ou reserva — o mesmo motivo pelo qual a aptidão do
 * instrumento (MTR-001) e o saldo de estoque (STK-002) também são derivados.
 *
 * <p>A travessia é em largura, e é de propósito: com corte de profundidade, largura devolve o
 * caminho mais curto até cada nó, que é o que alguém investigando quer ver primeiro.
 */
public final class Genealogy {

    /**
     * Teto de profundidade. Dez saltos cobrem com folga a cadeia real (insumo → OP → lote →
     * levedura → lote → plano → envase) e ainda deixam espaço para as gerações de levedura, que
     * são a única parte do grafo que cresce sem limite natural.
     */
    public static final int MAX_DEPTH = 10;

    private final Node root;
    private final Direction direction;
    private final int depth;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Gap> gaps;
    private final boolean truncated;

    private Genealogy(Node root, Direction direction, int depth, List<Node> nodes, List<Edge> edges,
            List<Gap> gaps, boolean truncated) {
        this.root = root;
        this.direction = direction;
        this.depth = depth;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.gaps = List.copyOf(gaps);
        this.truncated = truncated;
    }

    /**
     * Percorre o grafo a partir da raiz.
     *
     * @param depth número máximo de saltos; {@code 1..MAX_DEPTH}
     * @throws DepthExceededException quando a profundidade pedida passa do teto
     */
    public static Genealogy walk(Node root, Direction direction, int depth, LineageGraph graph) {
        Objects.requireNonNull(root, "nó raiz é obrigatório");
        Objects.requireNonNull(direction, "sentido da travessia é obrigatório");
        Objects.requireNonNull(graph, "grafo é obrigatório");
        if (depth < 1) {
            throw new IllegalArgumentException("profundidade deve ser pelo menos 1");
        }
        if (depth > MAX_DEPTH) {
            throw new DepthExceededException(depth, MAX_DEPTH);
        }

        // O rótulo do nó chega pelos provedores; o mapa guarda o primeiro que descrever cada nó,
        // porque a identidade (tipo+id) ignora rótulo e o segundo encontro traria o mesmo nó.
        var descriptions = new HashMap<Node, Node>();
        describe(descriptions, root);

        var visited = new HashSet<Node>();
        var collectedEdges = new LinkedHashSet<Edge>();
        var collectedGaps = new LinkedHashSet<Gap>();
        var queue = new ArrayDeque<Step>();
        queue.add(new Step(root, 0));
        visited.add(root);
        var truncated = false;

        while (!queue.isEmpty()) {
            var current = queue.poll();
            collectedGaps.addAll(graph.gapsOf(current.node()));
            var neighbours = graph.edgesOf(current.node(), direction);

            for (Edge edge : neighbours) {
                describe(descriptions, edge.from());
                describe(descriptions, edge.to());
                var other = edge.from().equals(current.node()) ? edge.to() : edge.from();

                if (current.distance() == depth) {
                    // Fronteira: não expande, mas declara que o grafo continua além do corte.
                    // Silenciar isso faria um recall parecer completo quando não é.
                    if (!visited.contains(other)) {
                        truncated = true;
                    }
                    continue;
                }

                collectedEdges.add(edge);
                if (visited.add(other)) {
                    queue.add(new Step(other, current.distance() + 1));
                }
            }
        }

        var nodes = visited.stream()
                .map(node -> descriptions.getOrDefault(node, node))
                .sorted(Comparator.comparing((Node node) -> node.type().ordinal())
                        .thenComparing(node -> node.id().toString()))
                .toList();
        var edges = collectedEdges.stream()
                .sorted(Comparator.comparing(Genealogy::edgeOrder))
                .toList();
        return new Genealogy(descriptions.getOrDefault(root, root), direction, depth, nodes,
                relabel(edges, descriptions), List.copyOf(collectedGaps), truncated);
    }

    /** Ordena por quando o elo passou a existir; sem data, por tipo e id, para a saída ser estável. */
    private static String edgeOrder(Edge edge) {
        var when = edge.recordedAt() == null ? "9999" : edge.recordedAt().toString();
        return when + '|' + edge.from().type() + '|' + edge.from().id() + '|' + edge.to().id();
    }

    private static void describe(Map<Node, Node> descriptions, Node node) {
        if (node.label() != null) {
            descriptions.putIfAbsent(node, node);
        }
    }

    /** Reescreve as pontas das arestas com o melhor rótulo conhecido de cada nó. */
    private static List<Edge> relabel(List<Edge> edges, Map<Node, Node> descriptions) {
        var relabelled = new ArrayList<Edge>(edges.size());
        for (Edge edge : edges) {
            relabelled.add(new Edge(descriptions.getOrDefault(edge.from(), edge.from()),
                    descriptions.getOrDefault(edge.to(), edge.to()),
                    edge.kind(), edge.strength(), edge.recordedAt()));
        }
        return relabelled;
    }

    public Node root() {
        return root;
    }

    public Direction direction() {
        return direction;
    }

    public int depth() {
        return depth;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Gap> gaps() {
        return gaps;
    }

    /** Verdadeiro quando o corte de profundidade escondeu parte do grafo. */
    public boolean truncated() {
        return truncated;
    }

    /** Só a raiz, sem aresta nenhuma: o nó existe e não está ligado a nada. */
    public boolean isolated() {
        return edges.isEmpty();
    }

    /** Arestas que são intenção, não fato — a leitura precisa saber distinguir. */
    public List<Edge> intendedEdges() {
        return edges.stream().filter(edge -> edge.strength() == EdgeStrength.INTENDED).toList();
    }

    private record Step(Node node, int distance) {}

    /** Nós alcançados, sem contar a raiz. */
    public int reach() {
        Set<Node> others = new HashSet<>(nodes);
        others.remove(root);
        return others.size();
    }
}
