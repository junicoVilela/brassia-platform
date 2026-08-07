package br.com.brew.brassia.reporting.application.port.inbound;

import br.com.brew.brassia.reporting.domain.BatchReport;
import java.util.UUID;

/** Leitura do relatório do lote (RPT-001). */
public interface BatchReportQueries {

    /**
     * O dossiê do lote, montado agora a partir do que cada módulo responde.
     *
     * @throws br.com.brew.brassia.reporting.domain.UnknownBatchReportException lote inexistente
     */
    BatchReport ofBatch(UUID breweryId, UUID batchId);
}
