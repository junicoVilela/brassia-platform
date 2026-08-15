package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.LotAvailabilityRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLotAvailabilityRepository implements LotAvailabilityRepository {

    private final JdbcClient jdbc;

    JdbcLotAvailabilityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ensure(UUID breweryId, UUID finishedLotId, int totalUnits) {
        // ON CONFLICT DO NOTHING: a linha nasce na primeira reserva do lote, e a segunda tentativa não
        // pode sobrescrever o reservado que já existe.
        jdbc.sql("""
                INSERT INTO sales_lot_availability (finished_lot_id, brewery_id, total_units, reserved_units)
                VALUES (:lot, :brewery, :total, 0)
                ON CONFLICT (finished_lot_id) DO NOTHING
                """)
                .param("lot", finishedLotId).param("brewery", breweryId).param("total", totalUnits)
                .update();
    }

    @Override
    public boolean reserve(UUID breweryId, UUID finishedLotId, int units) {
        // A condição vai no WHERE, e não numa leitura anterior seguida de UPDATE.
        //
        // Duas requisições simultâneas disputam a MESMA LINHA: a segunda fica bloqueada até o commit da
        // primeira e então relê `reserved_units` já atualizado. Se não couber, o UPDATE afeta zero
        // linhas — e é assim que quem perdeu a corrida descobre, sem que ninguém precise de lock
        // explícito nem de retry.
        var linhas = jdbc.sql("""
                UPDATE sales_lot_availability
                SET reserved_units = reserved_units + :units
                WHERE finished_lot_id = :lot AND brewery_id = :brewery
                  AND reserved_units + :units <= total_units
                """)
                .param("units", units).param("lot", finishedLotId).param("brewery", breweryId)
                .update();
        return linhas == 1;
    }

    @Override
    public void release(UUID breweryId, UUID finishedLotId, int units) {
        // GREATEST(0, ...) não é paranoia: sem ele, um cancelamento repetido levaria o reservado a
        // negativo, o CHECK derrubaria a transação e o pedido ficaria preso num estado que ninguém
        // consegue desfazer. O agregado já recusa cancelar duas vezes; isto é a rede embaixo.
        jdbc.sql("""
                UPDATE sales_lot_availability
                SET reserved_units = GREATEST(0, reserved_units - :units)
                WHERE finished_lot_id = :lot AND brewery_id = :brewery
                """)
                .param("units", units).param("lot", finishedLotId).param("brewery", breweryId)
                .update();
    }

    @Override
    public Map<UUID, Integer> freeUnits(UUID breweryId, Set<UUID> finishedLotIds) {
        if (finishedLotIds.isEmpty()) {
            return Map.of();
        }
        var out = new HashMap<UUID, Integer>();
        jdbc.sql("""
                SELECT finished_lot_id, total_units - reserved_units AS free
                FROM sales_lot_availability
                WHERE brewery_id = :brewery AND finished_lot_id IN (:lots)
                """)
                .param("brewery", breweryId).param("lots", finishedLotIds)
                .query((rs, row) -> Map.entry(rs.getObject("finished_lot_id", UUID.class),
                        rs.getInt("free")))
                .list()
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }
}
