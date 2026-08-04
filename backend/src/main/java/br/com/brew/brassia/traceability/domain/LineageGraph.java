package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import java.util.List;

/**
 * O que a travessia precisa saber sobre o mundo: as arestas de um nó e as lacunas dele.
 *
 * <p>Existe para o domínio não conhecer a lista de módulos que respondem. Quem monta o grafo é a
 * camada de aplicação, reunindo as {@code LineageSource}; aqui só existe a pergunta.
 */
public interface LineageGraph {

    /** Arestas do nó no sentido pedido. Nó desconhecido devolve lista vazia, não erro. */
    List<Edge> edgesOf(Node node, Direction direction);

    /** Elos ausentes conhecidos a partir do nó. */
    List<Gap> gapsOf(Node node);
}
