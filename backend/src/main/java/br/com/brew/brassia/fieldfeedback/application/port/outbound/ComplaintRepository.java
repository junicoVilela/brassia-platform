package br.com.brew.brassia.fieldfeedback.application.port.outbound;

import br.com.brew.brassia.fieldfeedback.domain.ComplainantContact;
import br.com.brew.brassia.fieldfeedback.domain.FieldComplaint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintRepository {

    void insert(FieldComplaint complaint);

    /** Grava estado, encerramento e destinos das ações. O relato em si não se edita. */
    void updateProgress(FieldComplaint complaint);

    Optional<FieldComplaint> find(UUID breweryId, UUID complaintId);

    Optional<FieldComplaint> findForUpdate(UUID breweryId, UUID complaintId);

    List<FieldComplaint> list(UUID breweryId, UUID batchId);

    // --- dado pessoal, deliberadamente em métodos separados ---

    void insertContact(ComplainantContact contact);

    /**
     * O contato.
     *
     * <p>Método próprio e não campo do complaint: quem carrega a reclamação para analisar off-flavor não
     * traz dado pessoal junto sem querer.
     */
    Optional<ComplainantContact> findContact(UUID breweryId, UUID complaintId);

    void eraseContact(UUID breweryId, UUID complaintId, java.time.Instant at);
}
