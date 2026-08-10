package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.Batch;
import java.util.List;
import java.util.UUID;

/**
 * Lista os lotes da cervejaria, paginado (REL-002).
 *
 * <p><strong>Por que deixou de devolver tudo.</strong> A listagem sem limite crescia com o histórico:
 * medido, 300 lotes respondiam em 40 ms e 3.000 em 319 ms — linear, cruzando a meta de 500 ms
 * (`docs/15_NONFUNCTIONAL_REQUIREMENTS.md`) por volta de 4.700 lotes. Uma cervejaria com três brassagens
 * por dia chega lá em poucos anos.
 *
 * <p>O limite máximo não é conforto de interface: sem teto, um cliente pedindo {@code size=100000}
 * reproduz exatamente o problema que a paginação existe para fechar.
 */
public interface ListBatchesUseCase {

    /** Teto de itens por página. Pedido acima disso é reduzido, não recusado. */
    int MAX_SIZE = 100;

    Page handle(Query query);

    record Query(UUID breweryId, int page, int size) {

        public Query {
            // Normaliza em vez de recusar: página negativa ou tamanho zero é engano de quem chama, não
            // ataque, e devolver 400 para `page=-1` só transforma um deslize em incidente de suporte.
            page = Math.max(0, page);
            size = Math.min(MAX_SIZE, Math.max(1, size));
        }

        public int offset() {
            return page * size;
        }
    }

    record Page(List<Batch> content, int page, int size, long totalElements) {

        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }
    }
}
