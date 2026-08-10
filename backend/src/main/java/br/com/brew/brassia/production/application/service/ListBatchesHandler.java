package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import java.util.Objects;

public final class ListBatchesHandler implements ListBatchesUseCase {

    private final BatchRepository repository;

    public ListBatchesHandler(BatchRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Page handle(Query query) {
        Objects.requireNonNull(query, "query");
        var total = repository.countByBrewery(query.breweryId());
        // A contagem vem antes: se a página pedida está além do fim, a consulta devolve lista vazia e o
        // total continua correto — a interface consegue dizer "página 9 de 3" em vez de "não há lotes".
        var content = repository.findPage(query.breweryId(), query.offset(), query.size());
        return new Page(content, query.page(), query.size(), total);
    }
}
