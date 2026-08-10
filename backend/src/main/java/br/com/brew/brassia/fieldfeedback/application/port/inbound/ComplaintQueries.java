package br.com.brew.brassia.fieldfeedback.application.port.inbound;

import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintQueries {

    Optional<FieldComplaint> find(UUID breweryId, UUID complaintId);

    List<FieldComplaint> list(UUID breweryId, UUID batchId);

    /**
     * O dado pessoal, numa operação à parte.
     *
     * <p><strong>Cada chamada é auditada</strong> — ver ComplaintHandler#contact. Ler dado pessoal é um
     * ato, não um efeito colateral de abrir a tela, e a auditoria é o que torna a diferença verificável.
     */
    Optional<ComplainantContact> contact(UUID breweryId, UUID complaintId, UUID actor);
}
