package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.domain.FinishedLot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFinishedLotRepository implements FinishedLotRepository {

    private static final String COLUMNS = """
            id, brewery_id, code, run_id, plan_id, batch_id, batch_code, container_id,
            container_volume_ml, units, volume_liters, packaged_on
            """;

    private final JdbcClient jdbc;

    JdbcFinishedLotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(FinishedLot lot) {
        jdbc.sql("""
                INSERT INTO packaging_finished_lot (%s)
                VALUES (:id, :brewery, :code, :run, :plan, :batch, :batchCode, :container,
                        :containerVolume, :units, :volume, :packagedOn)
                """.formatted(COLUMNS.replace("\n", " ").trim()))
                .param("id", lot.id())
                .param("brewery", lot.breweryId())
                .param("code", lot.code())
                .param("run", lot.runId())
                .param("plan", lot.planId())
                .param("batch", lot.batchId())
                .param("batchCode", lot.batchCode())
                .param("container", lot.containerId())
                .param("containerVolume", lot.containerVolumeMl())
                .param("units", lot.units())
                .param("volume", lot.volumeLiters())
                .param("packagedOn", lot.packagedOn())
                .update();
    }

    @Override
    public List<FinishedLot> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_finished_lot WHERE brewery_id = :brewery "
                        + "ORDER BY packaged_on DESC, code")
                .param("brewery", breweryId).query(this::map).list();
    }

    @Override
    public Optional<FinishedLot> findById(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_finished_lot "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id).query(this::map).optional();
    }

    @Override
    public List<FinishedLot> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_finished_lot "
                        + "WHERE brewery_id = :brewery AND batch_id = :batch ORDER BY code")
                .param("brewery", breweryId).param("batch", batchId).query(this::map).list();
    }

    @Override
    public Optional<FinishedLot> findByRun(UUID breweryId, UUID runId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM packaging_finished_lot "
                        + "WHERE brewery_id = :brewery AND run_id = :run")
                .param("brewery", breweryId).param("run", runId).query(this::map).optional();
    }

    @Override
    public int countByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("SELECT COUNT(*) FROM packaging_finished_lot "
                        + "WHERE brewery_id = :brewery AND batch_id = :batch")
                .param("brewery", breweryId).param("batch", batchId).query(Integer.class).single();
    }

    private FinishedLot map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return FinishedLot.rehydrate(rs.getObject("id", UUID.class), rs.getObject("brewery_id", UUID.class),
                rs.getString("code"), rs.getObject("run_id", UUID.class), rs.getObject("plan_id", UUID.class),
                rs.getObject("batch_id", UUID.class), rs.getString("batch_code"),
                rs.getObject("container_id", UUID.class), rs.getBigDecimal("container_volume_ml"),
                rs.getInt("units"), rs.getBigDecimal("volume_liters"),
                rs.getDate("packaged_on").toLocalDate());
    }
}
