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

    /**
     * Última leitura de cada grandeza do lote, e quantas leituras ele tem.
     *
     * <p>Existe para não carregar a série inteira só para ler a ponta dela. Desde que a telemetria
     * alimenta a fermentação (DEB-INT-001), um lote de duas semanas pode passar de 40 mil pontos — e
     * {@code findSeries} devolveria todos para o consumidor descartar 39.999.
     */
    Latest latestOf(UUID breweryId, UUID batchId);

    record UpsertResult(FermentationReading stored, boolean created) {}

    /** {@code null} em qualquer grandeza que o lote nunca teve medida. */
    record Latest(int count, FermentationReading density, FermentationReading temperature) {}
}
