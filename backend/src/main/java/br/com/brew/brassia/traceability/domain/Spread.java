package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.EdgeStrength;
import br.com.brew.brassia.traceability.LineageSource.Node;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Propagação de uma contenção pelo grafo (FDS-002): que nós um bloqueio alcança, e com que força.
 *
 * <p><strong>A intenção propaga, mas não se disfarça de fato.</strong> Um caminho que passa por
 * aresta {@code INTENDED} — hoje a reserva de insumo, que registra o lote separado para a OP e não
 * o que foi ao moinho — alcança o nó como <em>suspeito</em>, não como atingido. Bloquear os dois é
 * decisão de negócio deliberada: o custo de bloquear a mais é estoque parado, o de bloquear a menos
 * é produto na rua. O que não se pode é bloquear sem dizer qual dos dois casos é, porque quem
 * investiga precisa saber onde apertar primeiro.
 *
 * <p>Quando dois caminhos chegam ao mesmo nó, o <strong>confirmado vence</strong>: basta um caminho
 * de fato para que o alcance deixe de ser suposição.
 */
public final class Spread {

    private final Node origin;
    private final Direction direction;
    private final Map<Node, Boolean> suspectedByNode;
    private final boolean truncated;

    private Spread(Node origin, Direction direction, Map<Node, Boolean> suspectedByNode, boolean truncated) {
        this.origin = origin;
        this.direction = direction;
        this.suspectedByNode = Map.copyOf(suspectedByNode);
        this.truncated = truncated;
    }

    /**
     * Percorre o grafo a partir da origem acumulando a força do caminho.
     *
     * @param direction {@code FORWARD} responde "o que este lote contaminou"; {@code BACKWARD},
     *                  "que bloqueio alcança este nó" — a mesma travessia, lida dos dois lados
     * @param depth     saltos máximos; {@code 1..Genealogy.MAX_DEPTH}
     */
    public static Spread from(Node origin, Direction direction, int depth, LineageGraph graph) {
        Objects.requireNonNull(origin, "nó de origem é obrigatório");
        Objects.requireNonNull(direction, "sentido da propagação é obrigatório");
        Objects.requireNonNull(graph, "grafo é obrigatório");
        if (depth < 1) {
            throw new IllegalArgumentException("profundidade deve ser pelo menos 1");
        }
        if (depth > Genealogy.MAX_DEPTH) {
            throw new DepthExceededException(depth, Genealogy.MAX_DEPTH);
        }

        var suspected = new HashMap<Node, Boolean>();
        var labels = new HashMap<Node, Node>();
        var queue = new ArrayDeque<Step>();
        suspected.put(origin, false);
        label(labels, origin);
        queue.add(new Step(origin, false, 0));
        var truncated = false;

        while (!queue.isEmpty()) {
            var current = queue.poll();
            for (Edge edge : graph.edgesOf(current.node(), direction)) {
                label(labels, edge.from());
                label(labels, edge.to());
                var other = edge.from().equals(current.node()) ? edge.to() : edge.from();
                var reachSuspected = current.suspected() || edge.strength() == EdgeStrength.INTENDED;

                if (current.distance() == depth) {
                    // Contenção cortada pela profundidade se declara: uma que parece completa sem
                    // ser é pior do que uma declaradamente parcial.
                    if (!suspected.containsKey(other)) {
                        truncated = true;
                    }
                    continue;
                }
                // Confirmado vence suspeito, e por isso um nó já visto pode ser reexpandido.
                var known = suspected.get(other);
                if (known == null || (known && !reachSuspected)) {
                    suspected.put(other, reachSuspected);
                    queue.add(new Step(other, reachSuspected, current.distance() + 1));
                }
            }
        }

        var labelled = new HashMap<Node, Boolean>();
        suspected.forEach((node, isSuspected) -> labelled.put(labels.getOrDefault(node, node), isSuspected));
        return new Spread(labels.getOrDefault(origin, origin), direction, labelled, truncated);
    }

    private static void label(Map<Node, Node> labels, Node node) {
        if (node.label() != null) {
            labels.putIfAbsent(node, node);
        }
    }

    public Node origin() {
        return origin;
    }

    public Direction direction() {
        return direction;
    }

    /** Nós alcançados, sem a origem, em ordem estável. */
    public List<Affected> affected() {
        return suspectedByNode.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(origin))
                .map(entry -> new Affected(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing((Affected a) -> a.node().type().ordinal())
                        .thenComparing(a -> a.node().id().toString()))
                .toList();
    }

    /** Se o alcance chega a este nó, e com que força. A origem alcança a si mesma, confirmada. */
    public Optional<Affected> reaching(Node node) {
        var isSuspected = suspectedByNode.get(node);
        return isSuspected == null ? Optional.empty() : Optional.of(new Affected(node, isSuspected));
    }

    /** Verdadeiro quando o corte de profundidade escondeu parte do alcance. */
    public boolean truncated() {
        return truncated;
    }

    /**
     * Nó alcançado pela contenção.
     *
     * @param suspected verdadeiro quando algum elo do caminho é intenção, não fato registrado
     */
    public record Affected(Node node, boolean suspected) {

        public Affected {
            Objects.requireNonNull(node, "nó é obrigatório");
        }
    }

    private record Step(Node node, boolean suspected, int distance) {}
}
