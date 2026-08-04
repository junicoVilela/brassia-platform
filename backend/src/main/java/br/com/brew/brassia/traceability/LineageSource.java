package br.com.brew.brassia.traceability;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fonte de arestas de genealogia (TRC-001), implementada por cada módulo dono do dado.
 *
 * <p><strong>Por que não é uma CTE recursiva única.</strong> A genealogia atravessa estoque,
 * planejamento, produção, fermentação e envase. Uma consulta só, recursiva, teria de ler as
 * tabelas dos cinco — exatamente o que as fronteiras de módulo existem para impedir. Em vez
 * disso, cada módulo responde pelas arestas que nascem nas suas próprias tabelas e a travessia
 * acontece no domínio de rastreabilidade.
 *
 * <p>O efeito colateral é o melhor argumento a favor: módulo que não implementa esta porta não
 * contribui aresta nenhuma, e a lacuna aparece como lacuna — em vez de virar uma junção
 * silenciosamente vazia no meio de um SQL grande.
 *
 * <p>Implementação nova entra sozinha: o serviço injeta a lista de todas e não sabe quais existem.
 */
public interface LineageSource {

    /** Arestas em que o nó é a origem — o que veio <em>dele</em>, no sentido do tempo. */
    List<Edge> descendantsOf(UUID breweryId, Node node);

    /** Arestas em que o nó é o destino — o que o <em>originou</em>. */
    List<Edge> ancestorsOf(UUID breweryId, Node node);

    /**
     * Elos que deveriam existir a partir deste nó e não existem, com o motivo.
     *
     * <p>É o critério da história: "ausência de elo é evidenciada". Uma lacuna declarada é
     * rastreabilidade; uma lacuna silenciosa é a ilusão dela.
     */
    default List<Gap> gapsOf(UUID breweryId, Node node) {
        return List.of();
    }

    /** Resolve o rótulo de um nó deste tipo, quando o nó é a raiz da consulta e ninguém o descreveu. */
    default Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        return Optional.empty();
    }

    /** O que um nó pode ser. Ampliar esta lista é ampliar o alcance da rastreabilidade. */
    enum NodeType {
        /** Lote de insumo ou de embalagem no estoque. */
        STOCK_LOT,
        /** Ordem de produção. */
        BREW_ORDER,
        /** Lote de produção. */
        BATCH,
        /** Coleta de levedura, que liga um lote ao seguinte. */
        YEAST_HARVEST,
        /** Plano de envase. */
        PACKAGING_PLAN,
        /** Execução do envase. */
        PACKAGING_RUN
    }

    /**
     * O quanto a aresta prova.
     *
     * <p>{@code CONFIRMED} é registro do que aconteceu — o envase consumiu esta embalagem, esta
     * OP virou este lote. {@code INTENDED} é registro do que se pretendia: a reserva de insumo
     * diz qual lote foi separado para a OP, não qual foi de fato ao moinho. A distinção não é
     * preciosismo — num recall, tratar intenção como fato é recolher o lote errado.
     */
    enum EdgeStrength {
        CONFIRMED,
        INTENDED
    }

    /**
     * Nó do grafo.
     *
     * <p><strong>A identidade é o par tipo+id; o rótulo não distingue nós.</strong> O mesmo lote
     * descoberto por dois caminhos chega com rótulos montados por provedores diferentes, e se o
     * rótulo entrasse na igualdade ele viraria dois nós — a travessia deixaria de fechar ciclo e
     * andaria em círculo.
     */
    record Node(NodeType type, UUID id, String label) {

        public Node {
            Objects.requireNonNull(type, "tipo do nó é obrigatório");
            Objects.requireNonNull(id, "id do nó é obrigatório");
        }

        /** Nó sem rótulo, para quando só se conhece a referência. */
        public static Node of(NodeType type, UUID id) {
            return new Node(type, id, null);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Node node && type == node.type && id.equals(node.id);
        }

        @Override
        public int hashCode() {
            return type.hashCode() * 31 + id.hashCode();
        }
    }

    /**
     * Aresta dirigida no sentido do tempo: {@code from} deu origem a {@code to}.
     *
     * @param kind       o que a ligação é, em termos de negócio ("reserva de insumo", "envase")
     * @param recordedAt quando o elo passou a existir, para ordenar a leitura do grafo
     */
    record Edge(Node from, Node to, String kind, EdgeStrength strength, Instant recordedAt) {

        public Edge {
            Objects.requireNonNull(from, "origem da aresta é obrigatória");
            Objects.requireNonNull(to, "destino da aresta é obrigatório");
            Objects.requireNonNull(kind, "natureza da aresta é obrigatória");
            Objects.requireNonNull(strength, "força da aresta é obrigatória");
        }
    }

    /**
     * Elo ausente.
     *
     * @param expectedLink o elo que se esperava encontrar
     * @param reason       por que ele não existe — o que falta na plataforma para que exista
     */
    record Gap(Node from, String expectedLink, String reason) {

        public Gap {
            Objects.requireNonNull(from, "nó de origem da lacuna é obrigatório");
            Objects.requireNonNull(expectedLink, "elo esperado é obrigatório");
            Objects.requireNonNull(reason, "motivo da lacuna é obrigatório");
        }
    }
}
