package br.com.brew.brassia.fieldfeedback.application.port.inbound;

import br.com.brew.brassia.fieldfeedback.domain.ComplaintCategory;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import br.com.brew.brassia.fieldfeedback.domain.RequiredAction;
import br.com.brew.brassia.fieldfeedback.domain.SampleRetention;
import br.com.brew.brassia.fieldfeedback.domain.Severity;
import br.com.brew.brassia.fieldfeedback.domain.StorageReport;
import java.util.UUID;

/** Registrar e tratar reclamações de campo (FLD-001). */
public interface ComplaintCommands {

    FieldComplaint register(RegisterCommand command);

    FieldComplaint startAnalysis(UUID breweryId, UUID complaintId, UUID actor);

    FieldComplaint fulfill(UUID breweryId, UUID complaintId, RequiredAction action, UUID referenceId,
            UUID actor);

    FieldComplaint waive(UUID breweryId, UUID complaintId, RequiredAction action, String justification,
            UUID actor);

    FieldComplaint close(UUID breweryId, UUID complaintId, String note, UUID actor);

    /**
     * @param contact opcional: reclamação anônima é reclamação. Exigir identificação para registrar um
     *                corpo estranho coletaria dado desnecessário e perderia o relato de quem não quis
     *                se identificar.
     */
    record RegisterCommand(UUID breweryId, UUID batchId, String reference, ComplaintCategory category,
            Severity severity, String description, StorageReport storage, SampleRetention sample,
            ContactInput contact, UUID actor) {
    }

    record ContactInput(String name, String email, String phone, String address) {
    }
}
