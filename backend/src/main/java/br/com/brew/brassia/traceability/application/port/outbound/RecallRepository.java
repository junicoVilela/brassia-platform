package br.com.brew.brassia.traceability.application.port.outbound;

import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência do recall (FDS-003). O escopo não está aqui: ele é derivado do grafo. */
public interface RecallRepository {

    /** Recall e a lista de destinos alcançados nascem no mesmo commit. */
    void insert(Recall recall, List<RecallNotification> notifications);

    Optional<Recall> findById(UUID breweryId, UUID id);

    Optional<Recall> findForUpdate(UUID breweryId, UUID id);

    List<Recall> findAll(UUID breweryId);

    List<RecallNotification> findNotifications(UUID breweryId, UUID recallId);

    Optional<RecallNotification> findNotification(UUID breweryId, UUID recallId, UUID notificationId);

    void updateNotification(UUID breweryId, RecallNotification notification);

    /** Destinos ainda sem comunicação registrada — é o que impede encerrar. */
    int countPending(UUID breweryId, UUID recallId);

    boolean updateStatus(Recall recall, long expectedVersion);

    long nextSequence(UUID breweryId, int year);
}
