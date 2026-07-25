package br.com.brew.brassia.security.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScimGroupMappingRepository {
    record Mapping(UUID securityGroupId, boolean active) {}

    record MappingView(String externalGroupId, UUID securityGroupId, boolean active) {}

    Optional<Mapping> findActive(UUID providerId, String externalGroupId);
    void create(UUID providerId, String externalGroupId, UUID securityGroupId);

    /** Todos os mapeamentos de um provedor (ativos e inativos) para administração (SEC-B05). */
    List<MappingView> listByProvider(UUID providerId);

    /** Cria ou reativa o mapeamento apontando para o grupo informado. */
    void upsert(UUID providerId, String externalGroupId, UUID securityGroupId);

    /** Desativa o mapeamento (não remove; mantém histórico). */
    void deactivate(UUID providerId, String externalGroupId);
}
