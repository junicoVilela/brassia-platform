package br.com.brew.brassia.equipment.adapter.outbound.persistence;

import br.com.brew.brassia.equipment.application.port.outbound.CleanlinessRepository;
import br.com.brew.brassia.equipment.domain.Cleanliness;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCleanlinessRepository implements CleanlinessRepository {

    private final JdbcClient jdbc;

    JdbcCleanlinessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cleanliness> find(UUID breweryId, UUID equipmentId) {
        return jdbc.sql("""
                SELECT cleanliness, soiled_at, cleaned_at, cleaned_by_cycle_id
                FROM equipment WHERE brewery_id = :brewery AND id = :equipment
                """)
                .param("brewery", breweryId).param("equipment", equipmentId)
                .query((rs, n) -> new Cleanliness(
                        Cleanliness.State.valueOf(rs.getString("cleanliness")),
                        instantOf(rs.getTimestamp("soiled_at")),
                        instantOf(rs.getTimestamp("cleaned_at")),
                        rs.getObject("cleaned_by_cycle_id", UUID.class)))
                .optional();
    }

    @Override
    public java.util.Map<UUID, Cleanliness> findAll(UUID breweryId, java.util.Collection<UUID> equipmentIds) {
        if (equipmentIds.isEmpty()) {
            return java.util.Map.of();
        }
        var result = new java.util.LinkedHashMap<UUID, Cleanliness>();
        jdbc.sql("""
                SELECT id, cleanliness, soiled_at, cleaned_at, cleaned_by_cycle_id
                FROM equipment WHERE brewery_id = :brewery AND id IN (:ids)
                """)
                .param("brewery", breweryId).param("ids", equipmentIds)
                .query((rs, n) -> java.util.Map.entry(rs.getObject("id", UUID.class), new Cleanliness(
                        Cleanliness.State.valueOf(rs.getString("cleanliness")),
                        instantOf(rs.getTimestamp("soiled_at")),
                        instantOf(rs.getTimestamp("cleaned_at")),
                        rs.getObject("cleaned_by_cycle_id", UUID.class))))
                .list()
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public boolean save(UUID breweryId, UUID equipmentId, Cleanliness cleanliness) {
        // A versão otimista do perfil NÃO é tocada: limpar um tanque não é editar o equipamento, e
        // incrementá-la faria a edição de capacidade de outra pessoa falhar por conflito com uma
        // operação que não mexeu em medida nenhuma.
        return jdbc.sql("""
                UPDATE equipment
                SET cleanliness = :state, soiled_at = :soiled, cleaned_at = :cleaned,
                    cleaned_by_cycle_id = :cycle, updated_at = now()
                WHERE brewery_id = :brewery AND id = :equipment
                """)
                .param("state", cleanliness.state().name())
                .param("soiled", timestampOf(cleanliness.soiledAt()))
                .param("cleaned", timestampOf(cleanliness.cleanedAt()))
                .param("cycle", cleanliness.cleanedByCycleId())
                .param("brewery", breweryId).param("equipment", equipmentId)
                .update() == 1;
    }

    private static java.time.Instant instantOf(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestampOf(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
