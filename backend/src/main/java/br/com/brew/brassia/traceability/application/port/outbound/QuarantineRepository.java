package br.com.brew.brassia.traceability.application.port.outbound;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.Quarantine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência das quarentenas (FDS-002). Só a origem é guardada; o alcance é derivado. */
public interface QuarantineRepository {

    void insert(Quarantine quarantine);

    Optional<Quarantine> findById(UUID breweryId, UUID id);

    /** Carrega travando a linha, para que duas liberações concorrentes não passem as duas. */
    Optional<Quarantine> findForUpdate(UUID breweryId, UUID id);

    /** Quarentena aberta de um nó, se houver — é o que impede abrir a segunda. */
    Optional<Quarantine> findOpenFor(UUID breweryId, NodeType type, UUID nodeId);

    /** Todas as abertas da cervejaria: é a partir delas que qualquer bloqueio é decidido. */
    List<Quarantine> findOpen(UUID breweryId);

    List<Quarantine> findAll(UUID breweryId);

    /** Persiste a liberação com lock otimista; falso quando a versão mudou. */
    boolean updateStatus(Quarantine quarantine, long expectedVersion);
}
