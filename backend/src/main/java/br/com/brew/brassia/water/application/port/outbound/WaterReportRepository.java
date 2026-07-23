package br.com.brew.brassia.water.application.port.outbound;

import br.com.brew.brassia.water.domain.WaterReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterReportRepository {
    void insert(WaterReport report);

    /** Histórico da fonte, do laudo mais recente para o mais antigo. */
    List<WaterReport> findBySource(UUID breweryId, UUID sourceId);

    /** Laudo mais recente da fonte (base para a mistura). */
    Optional<WaterReport> findLatestBySource(UUID breweryId, UUID sourceId);
}
