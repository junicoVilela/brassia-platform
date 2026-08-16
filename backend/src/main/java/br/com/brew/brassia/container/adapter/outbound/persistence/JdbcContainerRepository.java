package br.com.brew.brassia.container.adapter.outbound.persistence;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerCondition;
import br.com.brew.brassia.container.domain.ContainerIdentifier;
import br.com.brew.brassia.container.domain.ContainerInspection;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.container.domain.DuplicateIdentifierException;
import br.com.brew.brassia.container.domain.IdentifierTechnology;
import br.com.brew.brassia.container.domain.Ownership;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContainerRepository implements ContainerRepository {

    private static final String COLUMNS = """
            id, brewery_id, code, kind, nominal_capacity_liters, ownership, condition, state,
            inspected_at, inspection_valid_until, inspected_by, inspection_note, retired_at,
            retirement_reason
            """;

    private final JdbcClient jdbc;

    JdbcContainerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Container c) {
        jdbc.sql("""
                INSERT INTO container (id, brewery_id, code, kind, nominal_capacity_liters, ownership,
                                       condition, state)
                VALUES (:id, :brewery, :code, :kind, :capacity, :ownership, :condition, :state)
                """)
                .param("id", c.id()).param("brewery", c.breweryId()).param("code", c.code())
                .param("kind", c.kind().name()).param("capacity", c.nominalCapacityLiters())
                .param("ownership", c.ownership().name()).param("condition", c.condition().name())
                .param("state", c.state().name())
                .update();
    }

    @Override
    public void update(Container c) {
        // O filtro por cervejaria vive no SQL, e não no chamador: é a barreira que o TenantIsolationTest
        // existe para cobrar, e a única que sobrevive a um handler distraído.
        var inspection = c.inspection().orElse(null);
        jdbc.sql("""
                UPDATE container SET ownership = :ownership, condition = :condition, state = :state,
                    inspected_at = :inspectedAt, inspection_valid_until = :validUntil,
                    inspected_by = :inspectedBy, inspection_note = :note, retired_at = :retiredAt,
                    retirement_reason = :reason, version = version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("ownership", c.ownership().name()).param("condition", c.condition().name())
                .param("state", c.state().name())
                .param("inspectedAt", inspection == null ? null : Timestamp.from(inspection.performedAt()))
                .param("validUntil", inspection == null ? null : Timestamp.from(inspection.validUntil()))
                .param("inspectedBy", inspection == null ? null : inspection.inspectedBy())
                .param("note", inspection == null ? null : inspection.note())
                .param("retiredAt", c.retiredAt().map(Timestamp::from).orElse(null))
                .param("reason", c.retirementReason().orElse(null))
                .param("id", c.id()).param("brewery", c.breweryId())
                .update();
    }

    @Override
    public Optional<Container> find(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM container WHERE id = :id AND brewery_id = :brewery")
                .param("id", id).param("brewery", breweryId)
                .query(JdbcContainerRepository::map).optional();
    }

    @Override
    public List<Container> list(UUID breweryId, String state) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container
                WHERE brewery_id = :brewery
                  AND (CAST(:state AS varchar) IS NULL OR state = CAST(:state AS varchar))
                ORDER BY code
                """)
                .param("brewery", breweryId).param("state", state)
                .query(JdbcContainerRepository::map).list();
    }

    @Override
    public void assign(ContainerIdentifier identifier) {
        try {
            jdbc.sql("""
                    INSERT INTO container_identifier (id, container_id, value, technology, assigned_at)
                    VALUES (:id, :container, :value, :tech, :at)
                    """)
                    .param("id", identifier.id()).param("container", identifier.containerId())
                    .param("value", identifier.value())
                    .param("tech", identifier.technology().name())
                    .param("at", Timestamp.from(identifier.assignedAt()))
                    .update();
        } catch (DuplicateKeyException emUso) {
            throw new DuplicateIdentifierException(identifier.value());
        }
    }

    @Override
    public void retireIdentifier(UUID breweryId, UUID identifierId, Instant at) {
        jdbc.sql("""
                UPDATE container_identifier SET retired_at = :at
                WHERE id = :id AND retired_at IS NULL
                  AND container_id IN (SELECT id FROM container WHERE brewery_id = :brewery)
                """)
                .param("at", Timestamp.from(at)).param("id", identifierId)
                .param("brewery", breweryId)
                .update();
    }

    @Override
    public List<ContainerIdentifier> identifiersOf(UUID containerId) {
        return jdbc.sql("""
                SELECT id, container_id, value, technology, assigned_at, retired_at
                FROM container_identifier WHERE container_id = :c ORDER BY assigned_at DESC
                """)
                .param("c", containerId)
                .query((rs, row) -> {
                    var retired = rs.getTimestamp("retired_at");
                    return ContainerIdentifier.reconstitute(rs.getObject("id", UUID.class),
                            rs.getObject("container_id", UUID.class), rs.getString("value"),
                            IdentifierTechnology.valueOf(rs.getString("technology")),
                            rs.getTimestamp("assigned_at").toInstant(),
                            retired == null ? null : retired.toInstant());
                })
                .list();
    }

    @Override
    public Optional<Container> resolve(UUID breweryId, String value) {
        // Só etiqueta VIVA resolve. A aposentada é história: deixá-la resolver faria uma leitura antiga
        // apontar para o keg errado depois de uma reetiquetagem.
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container c
                WHERE c.brewery_id = :brewery
                  AND EXISTS (SELECT 1 FROM container_identifier i
                              WHERE i.container_id = c.id AND i.value = :value
                                AND i.retired_at IS NULL)
                """)
                .param("brewery", breweryId).param("value", value)
                .query(JdbcContainerRepository::map).optional();
    }

    private static Container map(ResultSet rs, int row) throws SQLException {
        var inspectedAt = rs.getTimestamp("inspected_at");
        var retiredAt = rs.getTimestamp("retired_at");
        return Container.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getString("code"),
                ContainerKind.valueOf(rs.getString("kind")),
                rs.getBigDecimal("nominal_capacity_liters"),
                Ownership.valueOf(rs.getString("ownership")),
                ContainerCondition.valueOf(rs.getString("condition")),
                ContainerState.valueOf(rs.getString("state")),
                inspectedAt == null ? null
                        : new ContainerInspection(inspectedAt.toInstant(),
                                rs.getTimestamp("inspection_valid_until").toInstant(),
                                rs.getObject("inspected_by", UUID.class),
                                rs.getString("inspection_note")),
                retiredAt == null ? null : retiredAt.toInstant(),
                rs.getString("retirement_reason"));
    }
}
