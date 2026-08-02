package br.com.brew.brassia.gas.adapter.outbound.persistence;

import br.com.brew.brassia.gas.application.port.outbound.GasNetworkComponentRepository;
import br.com.brew.brassia.gas.domain.ComponentKind;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGasNetworkComponentRepository implements GasNetworkComponentRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, kind, code, name, max_pressure_bar, set_pressure_bar, active, version
            FROM gas_network_component
            """;

    private final JdbcClient jdbc;

    JdbcGasNetworkComponentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(GasNetworkComponent c) {
        jdbc.sql("""
                INSERT INTO gas_network_component (id, brewery_id, kind, code, name, max_pressure_bar,
                    set_pressure_bar, active, version)
                VALUES (:id, :brewery, :kind, :code, :name, :max, :set, :active, 0)
                """)
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("kind", c.kind().name())
                .param("code", c.code())
                .param("name", c.name())
                .param("max", c.maxPressureBar())
                .param("set", c.setPressureBar())
                .param("active", c.active())
                .update();
    }

    @Override
    public Optional<GasNetworkComponent> findById(UUID breweryId, UUID componentId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", componentId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<GasNetworkComponent> findAll(UUID breweryId, ComponentKind kind) {
        var sql = COLUMNS + " WHERE brewery_id = :brewery" + (kind == null ? "" : " AND kind = :kind")
                + " ORDER BY kind, code";
        var statement = jdbc.sql(sql).param("brewery", breweryId);
        if (kind != null) {
            statement = statement.param("kind", kind.name());
        }
        return statement.query((rs, n) -> map(rs)).list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM gas_network_component WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public boolean update(GasNetworkComponent c, long expectedVersion) {
        return jdbc.sql("""
                UPDATE gas_network_component
                SET name = :name, max_pressure_bar = :max, set_pressure_bar = :set, active = :active,
                    version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("name", c.name())
                .param("max", c.maxPressureBar())
                .param("set", c.setPressureBar())
                .param("active", c.active())
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    private GasNetworkComponent map(ResultSet rs) throws SQLException {
        return GasNetworkComponent.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                ComponentKind.valueOf(rs.getString("kind")),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("max_pressure_bar"),
                rs.getBigDecimal("set_pressure_bar"),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }
}
