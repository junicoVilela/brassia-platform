package br.com.brew.brassia.traceability;

import java.util.List;
import java.util.UUID;

/**
 * De onde o lote veio e para onde ele foi, resumido (RPT-001).
 *
 * <p>O relatório não quer o grafo: quer as duas pontas. Quem lê um relatório de lote precisa saber
 * que insumos entraram e que expedições saíram — a topologia do meio é assunto da tela de
 * genealogia, que existe justamente para isso.
 *
 * <p><strong>As lacunas do grafo viajam junto.</strong> Um relatório que lista três lotes de
 * origem sem dizer que um quarto elo está faltando afirma rastreabilidade que não tem.
 */
public interface BatchLineageLookup {

    BatchLineage ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param gaps      elos que a genealogia não conseguiu seguir, em texto legível
     * @param truncated a travessia bateu no teto de profundidade: há mais grafo além do que se vê
     */
    record BatchLineage(List<LineageEntry> origins, List<LineageEntry> destinations, List<String> gaps,
            boolean truncated) {

        public BatchLineage {
            origins = List.copyOf(origins);
            destinations = List.copyOf(destinations);
            gaps = List.copyOf(gaps);
        }

        public static BatchLineage empty() {
            return new BatchLineage(List.of(), List.of(), List.of(), false);
        }

        public boolean complete() {
            return gaps.isEmpty() && !truncated;
        }
    }

    record LineageEntry(String type, String label) {}
}
