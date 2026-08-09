package br.com.brew.brassia.sensor.adapter.outbound.persistence;

import br.com.brew.brassia.sensor.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.sensor.domain.Lateness;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.ReadingQuality;
import br.com.brew.brassia.sensor.domain.SensorReading;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Leituras em PostgreSQL (INT-001).
 *
 * <p><strong>Aqui não existe {@code UPDATE} nem {@code DELETE}, e isso é a regra e não um esquecimento.</strong>
 * Medição está entre o que o AGENTS.md põe fora do alcance de alteração. Um sensor que se corrige manda
 * outra leitura.
 */
@Repository
class JdbcSensorReadingRepository implements ReadingRepository {

    private static final String COLUMNS = """
            id, brewery_id, device_id, message_id, measure, value, unit, measured_at, received_at,
            quality, quality_reason, delay_seconds, late
            """;

    private final JdbcClient jdbc;

    JdbcSensorReadingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A idempotência, resolvida pelo banco.
     *
     * <p>{@code ON CONFLICT DO NOTHING} sobre {@code uq_sensor_reading_message} não é uma otimização de
     * uma consulta prévia — é a única forma de a decisão ser atômica. Duas requisições simultâneas com o
     * mesmo {@code message_id} chegam as duas ao {@code INSERT}; uma insere, a outra recebe zero linhas
     * afetadas e sabe, sem ambiguidade, que perdeu a corrida.
     *
     * <p>O conflito é <em>silencioso</em> de propósito: repetição não é erro. O dispositivo que reenviou
     * por não ter recebido o ACK fez a coisa certa, e uma exceção aqui obrigaria a camada de cima a
     * distinguir "duplicata" de "falha real" pelo texto de um {@code SQLException} — que é o tipo de
     * acoplamento a detalhe de driver que vira defeito na próxima atualização de versão.
     */
    @Override
    public boolean insertIfAbsent(SensorReading reading) {
        return jdbc.sql("""
                INSERT INTO sensor_reading (id, brewery_id, device_id, message_id, measure, value, unit,
                        measured_at, received_at, quality, quality_reason, delay_seconds, late)
                VALUES (:id, :brewery, :device, :message, :measure, :value, :unit, :measured, :received,
                        :quality, :reason, :delay, :late)
                ON CONFLICT ON CONSTRAINT uq_sensor_reading_message DO NOTHING
                """)
                .param("id", reading.id())
                .param("brewery", reading.breweryId())
                .param("device", reading.deviceId())
                .param("message", reading.messageId())
                .param("measure", reading.measure().name())
                .param("value", reading.value())
                .param("unit", reading.unit())
                .param("measured", Timestamp.from(reading.measuredAt()))
                .param("received", Timestamp.from(reading.receivedAt()))
                .param("quality", reading.quality().name())
                .param("reason", reading.qualityReason())
                .param("delay", reading.lateness().delay().toSeconds())
                .param("late", reading.lateness().late())
                .update() == 1;
    }

    @Override
    public Optional<SensorReading> byMessageId(UUID breweryId, UUID deviceId, String messageId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sensor_reading "
                        + "WHERE brewery_id = :brewery AND device_id = :device AND message_id = :message")
                .param("brewery", breweryId).param("device", deviceId).param("message", messageId)
                .query(this::map).optional();
    }

    /**
     * A janela é por {@code measured_at}, não por {@code received_at}.
     *
     * <p>Quem pergunta "o que aconteceu no tanque entre 8h e 12h" quer os fatos daquele intervalo, não as
     * mensagens que chegaram nele. Uma leitura das 9h represada por queda de rede e entregue às 14h
     * pertence à janela da manhã — filtrar pela chegada a esconderia justamente de quem investiga por que
     * a curva tem um buraco.
     */
    @Override
    public List<SensorReading> inWindow(UUID breweryId, UUID deviceId, Instant from, Instant to, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sensor_reading "
                        + "WHERE brewery_id = :brewery AND device_id = :device "
                        + "  AND measured_at >= :from AND measured_at < :to "
                        + "ORDER BY measured_at DESC LIMIT :limit")
                .param("brewery", breweryId).param("device", deviceId)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .param("limit", limit)
                .query(this::map).list();
    }

    private SensorReading map(ResultSet rs, int rowNum) throws SQLException {
        return SensorReading.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("message_id"),
                Measure.valueOf(rs.getString("measure")),
                rs.getBigDecimal("value"),
                rs.getString("unit"),
                rs.getTimestamp("measured_at").toInstant(),
                rs.getTimestamp("received_at").toInstant(),
                ReadingQuality.valueOf(rs.getString("quality")),
                rs.getString("quality_reason"),
                new Lateness(Duration.ofSeconds(rs.getLong("delay_seconds")), rs.getBoolean("late")));
    }
}
