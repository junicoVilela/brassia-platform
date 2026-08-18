package br.com.brew.brassia.forecast.adapter.outbound.persistence;

import br.com.brew.brassia.forecast.application.port.outbound.TankCycleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTankCycleRepository implements TankCycleRepository {

    private final JdbcClient jdbc;

    JdbcTankCycleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID breweryId, UUID equipmentId, int cycleDays, String note, UUID actor) {
        jdbc.sql("""
                INSERT INTO forecast_tank_cycle (brewery_id, equipment_id, cycle_days, note,
                                                 updated_by, updated_at)
                VALUES (:brewery, :equipment, :days, :note, :by, now())
                ON CONFLICT (brewery_id, equipment_id) DO UPDATE
                SET cycle_days = :days, note = :note, updated_by = :by, updated_at = now()
                """)
                .param("brewery", breweryId).param("equipment", equipmentId).param("days", cycleDays)
                .param("note", note).param("by", actor)
                .update();
    }

    @Override
    public void remove(UUID breweryId, UUID equipmentId) {
        // Tirar o tanque da conta é decisão legítima — ele saiu de operação, virou maturador. Sem isto, a
        // única forma de corrigir seria declarar um ciclo absurdo, e a capacidade mentiria em silêncio.
        jdbc.sql("DELETE FROM forecast_tank_cycle WHERE brewery_id = :b AND equipment_id = :e")
                .param("b", breweryId).param("e", equipmentId)
                .update();
    }

    @Override
    public List<TankCycle> cycles(UUID breweryId) {
        return jdbc.sql("""
                SELECT equipment_id, cycle_days, note FROM forecast_tank_cycle
                WHERE brewery_id = :brewery ORDER BY updated_at
                """)
                .param("brewery", breweryId)
                .query((rs, row) -> new TankCycle(rs.getObject("equipment_id", UUID.class),
                        rs.getInt("cycle_days"), rs.getString("note")))
                .list();
    }
}
