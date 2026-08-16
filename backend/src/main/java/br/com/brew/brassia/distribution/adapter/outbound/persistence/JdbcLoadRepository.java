package br.com.brew.brassia.distribution.adapter.outbound.persistence;

import br.com.brew.brassia.distribution.application.port.outbound.LoadRepository;
import br.com.brew.brassia.distribution.domain.DeliveryWindow;
import br.com.brew.brassia.distribution.domain.Load;
import br.com.brew.brassia.distribution.domain.LoadStatus;
import br.com.brew.brassia.distribution.domain.LoadStop;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLoadRepository implements LoadRepository {

    private final JdbcClient jdbc;

    JdbcLoadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Load load) {
        jdbc.sql("""
                INSERT INTO distribution_load (id, brewery_id, code, scheduled_for, capacity_liters,
                                               planned_by, status)
                VALUES (:id, :brewery, :code, :day, :capacity, :planner, :status)
                """)
                .param("id", load.id()).param("brewery", load.breweryId()).param("code", load.code())
                .param("day", load.scheduledFor()).param("capacity", load.capacityLiters())
                .param("planner", load.plannedBy()).param("status", load.status().name())
                .update();
    }

    /**
     * Regrava o agregado inteiro.
     *
     * <p>Uma carga é pequena e muda por inteiro. Atualizar em partes deixaria roteiro e itens divergirem
     * entre si — o defeito que só o romaneio impresso denuncia, e tarde demais.
     */
    @Override
    public void update(Load load) {
        var atualizadas = jdbc.sql("""
                UPDATE distribution_load SET driver_id = :driver, vehicle = :vehicle,
                    status = :status, released_by = :releasedBy, released_at = :releasedAt,
                    version = version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("driver", load.driverId().orElse(null))
                .param("vehicle", load.vehicle().orElse(null))
                .param("status", load.status().name())
                .param("releasedBy", load.releasedBy().orElse(null))
                .param("releasedAt", load.releasedAt().map(Timestamp::from).orElse(null))
                .param("id", load.id()).param("brewery", load.breweryId())
                .update();
        if (atualizadas == 0) {
            return;
        }
        // As paradas caem em cascata com os itens: reescrever é mais simples e mais seguro que
        // reconciliar, e o agregado é a fonte da verdade.
        jdbc.sql("DELETE FROM distribution_load_stop WHERE load_id = :id").param("id", load.id())
                .update();
        for (var stop : load.route()) {
            jdbc.sql("""
                    INSERT INTO distribution_load_stop (id, load_id, customer_id, customer_name,
                                                        sequence, window_from, window_to)
                    VALUES (:id, :load, :customer, :name, :seq, :from, :to)
                    """)
                    .param("id", stop.id()).param("load", load.id())
                    .param("customer", stop.customerId()).param("name", stop.customerName())
                    .param("seq", stop.sequence())
                    .param("from", stop.window().map(w -> Timestamp.from(w.from())).orElse(null))
                    .param("to", stop.window().map(w -> Timestamp.from(w.to())).orElse(null))
                    .update();
            for (var containerId : stop.containerIds()) {
                jdbc.sql("""
                        INSERT INTO distribution_load_item (id, stop_id, load_id, container_id,
                                                            volume_liters)
                        VALUES (:id, :stop, :load, :container, :volume)
                        """)
                        .param("id", UUID.randomUUID()).param("stop", stop.id())
                        .param("load", load.id()).param("container", containerId)
                        .param("volume", load.volumeOf(containerId))
                        .update();
            }
        }
    }

    @Override
    public Optional<Load> find(UUID breweryId, UUID id) {
        var load = jdbc.sql("""
                SELECT id, brewery_id, code, scheduled_for, capacity_liters, planned_by, driver_id,
                       vehicle, status, released_by, released_at
                FROM distribution_load WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", id).param("brewery", breweryId)
                .query((rs, row) -> {
                    var releasedAt = rs.getTimestamp("released_at");
                    return Load.reconstitute(rs.getObject("id", UUID.class),
                            rs.getObject("brewery_id", UUID.class), rs.getString("code"),
                            rs.getObject("scheduled_for", LocalDate.class),
                            rs.getBigDecimal("capacity_liters"),
                            rs.getObject("planned_by", UUID.class),
                            rs.getObject("driver_id", UUID.class), rs.getString("vehicle"),
                            LoadStatus.valueOf(rs.getString("status")),
                            rs.getObject("released_by", UUID.class),
                            releasedAt == null ? null : releasedAt.toInstant());
                })
                .optional();
        load.ifPresent(this::fillStops);
        return load;
    }

    @Override
    public List<Load> list(UUID breweryId, LocalDate day) {
        var ids = jdbc.sql("""
                SELECT id FROM distribution_load
                WHERE brewery_id = :brewery
                  AND (CAST(:day AS date) IS NULL OR scheduled_for = CAST(:day AS date))
                ORDER BY scheduled_for DESC, code
                """)
                .param("brewery", breweryId).param("day", day).query(UUID.class).list();
        return ids.stream().map(id -> find(breweryId, id)).flatMap(Optional::stream).toList();
    }

    @Override
    public Optional<String> openLoadWith(UUID breweryId, UUID containerId, UUID exceptLoadId) {
        return jdbc.sql("""
                SELECT l.code FROM distribution_load_item i
                JOIN distribution_load l ON l.id = i.load_id
                WHERE i.container_id = :container AND l.brewery_id = :brewery
                  AND l.status IN ('PLANNED', 'RELEASED', 'IN_ROUTE')
                  AND l.id <> :except
                LIMIT 1
                """)
                .param("container", containerId).param("brewery", breweryId)
                .param("except", exceptLoadId)
                .query(String.class).optional();
    }

    private void fillStops(Load load) {
        var stops = jdbc.sql("""
                SELECT id, customer_id, customer_name, sequence, window_from, window_to
                FROM distribution_load_stop WHERE load_id = :id ORDER BY sequence
                """)
                .param("id", load.id())
                .query((rs, row) -> {
                    var from = rs.getTimestamp("window_from");
                    return LoadStop.create(rs.getObject("id", UUID.class),
                            rs.getObject("customer_id", UUID.class), rs.getString("customer_name"),
                            rs.getInt("sequence"),
                            from == null ? null
                                    : new DeliveryWindow(from.toInstant(),
                                            rs.getTimestamp("window_to").toInstant()));
                })
                .list();
        // Reconstituir passa pelo agregado, e não por dentro dele: as mesmas regras que valeram na
        // montagem valem aqui, e uma linha inconsistente no banco aparece agora em vez de na rua.
        for (var stop : stops) {
            load.restoreStop(stop);
            jdbc.sql("SELECT container_id, volume_liters FROM distribution_load_item WHERE stop_id = :s")
                    .param("s", stop.id())
                    .query((rs, row) -> {
                        var containerId = rs.getObject("container_id", UUID.class);
                        load.restoreItem(stop.id(), containerId, rs.getBigDecimal("volume_liters"));
                        return containerId;
                    })
                    .list();
        }
    }
}
