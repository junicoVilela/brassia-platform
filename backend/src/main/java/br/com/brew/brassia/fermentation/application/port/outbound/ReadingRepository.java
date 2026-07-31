package br.com.brew.brassia.fermentation.application.port.outbound;

import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import java.util.List;
import java.util.UUID;

public interface ReadingRepository {

    /**
     * Ingestão idempotente pela chave natural (batch, grandeza, fonte, instante): grava a
     * leitura se ausente e retorna a leitura efetiva; um reenvio é no-op e retorna a leitura
     * já persistida (first-wins). {@code created} indica se houve inserção.
     */
    UpsertResult upsertIfAbsent(FermentationReading reading);

    /** Série temporal de um lote, opcionalmente filtrada por grandeza, ordenada por instante. */
    List<FermentationReading> findSeries(UUID breweryId, UUID batchId, ReadingKind kind);

    record UpsertResult(FermentationReading stored, boolean created) {}
}
