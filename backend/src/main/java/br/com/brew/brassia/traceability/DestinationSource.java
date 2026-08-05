package br.com.brew.brassia.traceability;

import br.com.brew.brassia.traceability.LineageSource.Node;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fonte de destinos alcançados por um escopo (FDS-003), implementada por cada módulo que registra
 * saída de produto — hoje só o envase, com a expedição (TRC-001-D).
 *
 * <p><strong>A porta é da rastreabilidade e o envase a implementa</strong>, e não o contrário. O
 * recall precisa de contato, não de rótulo de nó, mas se ele fosse buscar isso numa consulta
 * publicada do envase, rastreabilidade passaria a depender de envase — que já depende dela, pela
 * {@link LineageSource} e pela quarentena. O ciclo apareceu de verdade no {@code ModularityTest};
 * inverter a direção é o que o resolve, e é o mesmo desenho que a genealogia já usa.
 *
 * <p>Efeito colateral, igual ao da linhagem: quando existir expedição por outro caminho — venda
 * direta, doação, degustação —, o módulo dono implementa esta porta e o recall passa a alcançar
 * aquele destino sem que uma linha daqui mude.
 */
public interface DestinationSource {

    /** Destinos que partem destes nós do escopo. Nó desconhecido não contribui nada. */
    List<Destination> destinationsOf(UUID breweryId, List<Node> scope);

    /**
     * @param reference identificador da saída no módulo que a registrou — é por ele que o dossiê
     *                  reconhece um destino já comunicado de um descoberto depois
     * @param origin    o nó do escopo de onde a saída partiu (hoje, o lote de produto acabado)
     * @param contact   quem procurar; nulo é lacuna que o dossiê mostra, não esconde
     */
    record Destination(UUID reference, Node origin, String label, String contact, int units) {

        public Destination {
            Objects.requireNonNull(reference, "referência da saída é obrigatória");
            Objects.requireNonNull(origin, "nó de origem é obrigatório");
            Objects.requireNonNull(label, "destino é obrigatório");
        }
    }
}
