package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import java.util.List;
import java.util.UUID;

/**
 * Submete um payload ao pipeline de importação: recebe, valida e deixa o job em
 * {@code REVIEW_REQUIRED} (ou {@code FAILED} se houver erros). Não publica.
 */
public interface SubmitImportJobUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID sourceId, String datasetVersion, String contentType,
            String rawPayload) {}

    record Result(UUID jobId, String status, List<ValidationIssue> issues) {}
}
