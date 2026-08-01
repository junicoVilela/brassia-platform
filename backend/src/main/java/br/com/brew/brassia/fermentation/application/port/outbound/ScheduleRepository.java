package br.com.brew.brassia.fermentation.application.port.outbound;

import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {

    void insert(FermentationSchedule schedule);

    /** Regrava as etapas (janelas replanejadas, execução registrada). */
    void replaceSteps(FermentationSchedule schedule);

    Optional<FermentationSchedule> findByBatch(UUID breweryId, UUID batchId);

    Optional<FermentationSchedule> findById(UUID breweryId, UUID scheduleId);

    /** Agendas com ao menos uma etapa pendente, para varrer atrasos. */
    List<FermentationSchedule> findWithPendingSteps(UUID breweryId);
}
