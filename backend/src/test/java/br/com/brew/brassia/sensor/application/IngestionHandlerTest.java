package br.com.brew.brassia.sensor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.sensor.application.port.inbound.ReadingCommands;
import br.com.brew.brassia.sensor.application.port.outbound.BatchCurveFeed;
import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.sensor.application.service.IngestionHandler;
import br.com.brew.brassia.sensor.domain.DeviceStatus;
import br.com.brew.brassia.sensor.domain.InactiveDeviceException;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.ReadingQuality;
import br.com.brew.brassia.sensor.domain.SensorDevice;
import br.com.brew.brassia.sensor.domain.SensorReading;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A ingestão e a idempotência dela (INT-001).
 *
 * <p>Os dublês imitam o comportamento que a restrição única do banco garante — inserir só quando a chave
 * {@code (device, message)} ainda não existe. O comportamento real dessa restrição é exercido no
 * {@code SensorIngestionIT} contra PostgreSQL de verdade; aqui o que se testa é o que o caso de uso faz com
 * a resposta dela.
 */
class IngestionHandlerTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OUTRA_CERVEJARIA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant MEDIU = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant RECEBEU = Instant.parse("2026-08-09T10:00:30Z");

    private FakeDevices devices;
    private FakeReadings readings;
    private FakeCurve curve;
    private IngestionHandler handler;
    private SensorDevice termometro;

    @BeforeEach
    void setUp() {
        devices = new FakeDevices();
        readings = new FakeReadings();
        curve = new FakeCurve();
        handler = new IngestionHandler(devices, readings, curve, Clock.fixed(RECEBEU, ZoneOffset.UTC));
        termometro = SensorDevice.register(CERVEJARIA, "TANK-01-TEMP", "Termômetro 1", Measure.TEMPERATURE,
                "C", null, Duration.ofMinutes(5), OPERADOR, MEDIU);
        devices.save(termometro);
    }

    private static ReadingCommands.Request pedido(String messageId, String value) {
        return new ReadingCommands.Request(CERVEJARIA, "TANK-01-TEMP", messageId, "TEMPERATURE",
                new BigDecimal(value), "C", MEDIU);
    }

    @Test
    @DisplayName("leitura nova é gravada e não é duplicata")
    void leituraNovaEGravada() {
        var result = handler.ingest(pedido("msg-1", "18.5"));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.reading().quality()).isEqualTo(ReadingQuality.GOOD);
        assertThat(readings.stored).hasSize(1);
    }

    @Test
    @DisplayName("mensagem repetida não grava segunda linha e devolve duplicate=true")
    void repetidaEIdempotente() {
        // O critério da história. O dispositivo não recebeu o ACK e reenviou — comportamento correto dele.
        var primeira = handler.ingest(pedido("msg-1", "18.5"));
        var segunda = handler.ingest(pedido("msg-1", "18.5"));

        assertThat(primeira.duplicate()).isFalse();
        assertThat(segunda.duplicate()).isTrue();
        assertThat(readings.stored).hasSize(1);
    }

    @Test
    @DisplayName("a repetição devolve a leitura GRAVADA, não a recém-montada")
    void repetidaDevolveAGravada() {
        // As duas diferem no id e no receivedAt. Responder a segunda faria o dispositivo acreditar que a
        // medição chegou agora, apagando o atraso real do primeiro envio — que é o dado de quem investiga
        // por que a curva tem um buraco.
        var primeira = handler.ingest(pedido("msg-1", "18.5"));

        var relogioAdiantado = new IngestionHandler(devices, readings, curve,
                Clock.fixed(RECEBEU.plus(Duration.ofHours(2)), ZoneOffset.UTC));
        var segunda = relogioAdiantado.ingest(pedido("msg-1", "18.5"));

        assertThat(segunda.reading().id()).isEqualTo(primeira.reading().id());
        assertThat(segunda.reading().receivedAt()).isEqualTo(RECEBEU);
    }

    @Test
    @DisplayName("mesmo conteúdo com mensagem diferente é leitura nova, não duplicata")
    void mesmoConteudoComOutraMensagemENova() {
        // Sensor parado reporta o mesmo valor a cada janela. Tratar isso como repetição descartaria
        // leituras verdadeiras e faria a curva parecer interrompida justamente quando nada muda.
        handler.ingest(pedido("msg-1", "18.5"));
        var segunda = handler.ingest(pedido("msg-2", "18.5"));

        assertThat(segunda.duplicate()).isFalse();
        assertThat(readings.stored).hasSize(2);
    }

    @Test
    @DisplayName("valor fora da faixa é gravado e sinalizado, não recusado")
    void foraDaFaixaEGravado() {
        var result = handler.ingest(pedido("msg-1", "85"));

        assertThat(result.reading().quality()).isEqualTo(ReadingQuality.OUT_OF_RANGE);
        assertThat(readings.stored).hasSize(1);
    }

    @Test
    @DisplayName("atraso além do intervalo esperado é sinalizado")
    void atrasoESinalizado() {
        var atrasada = new IngestionHandler(devices, readings, curve,
                Clock.fixed(MEDIU.plus(Duration.ofMinutes(20)), ZoneOffset.UTC));

        var result = atrasada.ingest(pedido("msg-1", "18.5"));

        assertThat(result.reading().lateness().late()).isTrue();
        assertThat(result.reading().quality()).isEqualTo(ReadingQuality.GOOD);
    }

    @Test
    @DisplayName("a leitura gravada é encaminhada à curva, com o equipamento do dispositivo")
    void encaminhaAcurva() {
        // O sensor não sabe qual lote está no tanque — ele sabe o tanque. Quem resolve o resto é o
        // adapter; o que se verifica aqui é que o caso de uso entrega os dois dados certos.
        var fermentador = UUID.randomUUID();
        var comEquipamento = SensorDevice.register(CERVEJARIA, "TANK-02-TEMP", "Termômetro 2",
                Measure.TEMPERATURE, "C", fermentador, Duration.ofMinutes(5), OPERADOR, MEDIU);
        devices.save(comEquipamento);

        handler.ingest(new ReadingCommands.Request(CERVEJARIA, "TANK-02-TEMP", "msg-1", "TEMPERATURE",
                new BigDecimal("18.5"), "C", MEDIU));

        assertThat(curve.forwarded).hasSize(1);
        assertThat(curve.forwarded.get(0).equipmentId()).isEqualTo(fermentador);
        assertThat(curve.forwarded.get(0).reading().value()).isEqualByComparingTo("18.5");
    }

    @Test
    @DisplayName("a REENTREGA não encaminha de novo")
    void reentregaNaoEncaminha() {
        // Encaminhar de novo seria inofensivo — a leitura de fermentação é idempotente pela chave natural
        // — mas seria trabalho por engano justamente no caminho que existe para ser barato. E se um dia
        // deixar de ser idempotente do outro lado, o erro apareceria como ponto duplicado na curva.
        handler.ingest(pedido("msg-1", "18.5"));
        handler.ingest(pedido("msg-1", "18.5"));

        assertThat(readings.stored).hasSize(1);
        assertThat(curve.forwarded).hasSize(1);
    }

    @Test
    @DisplayName("dispositivo de outra cervejaria é desconhecido, não 'existe mas não é seu'")
    void outraCervejariaEDesconhecido() {
        var deOutra = new ReadingCommands.Request(OUTRA_CERVEJARIA, "TANK-01-TEMP", "msg-1",
                "TEMPERATURE", new BigDecimal("18.5"), "C", MEDIU);

        assertThatThrownBy(() -> handler.ingest(deOutra)).isInstanceOf(UnknownDeviceException.class);
        assertThat(readings.stored).isEmpty();
    }

    @Test
    @DisplayName("código é normalizado antes da busca: a etiqueta e o firmware escrevem diferente")
    void normalizaCodigoNaBusca() {
        var minusculo = new ReadingCommands.Request(CERVEJARIA, " tank-01-temp ", "msg-1", "TEMPERATURE",
                new BigDecimal("18.5"), "C", MEDIU);

        assertThat(handler.ingest(minusculo).duplicate()).isFalse();
    }

    @Test
    @DisplayName("dispositivo pausado recusa a leitura em vez de gravá-la sinalizada")
    void pausadoRecusa() {
        devices.save(termometro.changeStatusTo(DeviceStatus.PAUSED));

        assertThatThrownBy(() -> handler.ingest(pedido("msg-1", "18.5")))
                .isInstanceOf(InactiveDeviceException.class);
        assertThat(readings.stored).isEmpty();
    }

    @Test
    @DisplayName("grandeza divergente do cadastro é recusada, e nada é gravado")
    void grandezaDivergenteRecusa() {
        var pressao = new ReadingCommands.Request(CERVEJARIA, "TANK-01-TEMP", "msg-1", "PRESSURE",
                new BigDecimal("12"), "PSI", MEDIU);

        assertThatThrownBy(() -> handler.ingest(pressao)).isInstanceOf(IllegalArgumentException.class);
        assertThat(readings.stored).isEmpty();
    }

    @Test
    @DisplayName("unidade divergente do cadastro é recusada: firmware que passou a mandar Fahrenheit")
    void unidadeDivergenteRecusa() {
        var fahrenheit = new ReadingCommands.Request(CERVEJARIA, "TANK-01-TEMP", "msg-1", "TEMPERATURE",
                new BigDecimal("65"), "F", MEDIU);

        assertThatThrownBy(() -> handler.ingest(fahrenheit)).isInstanceOf(IllegalArgumentException.class);
        assertThat(readings.stored).isEmpty();
    }

    /** Dublê do repositório de dispositivos. */
    /** Registra o que foi encaminhado à curva, para separar "não encaminhou" de "encaminhou errado". */
    private static final class FakeCurve implements BatchCurveFeed {

        private final List<Forwarded> forwarded = new ArrayList<>();

        @Override
        public void forward(SensorReading reading, UUID equipmentId) {
            forwarded.add(new Forwarded(reading, equipmentId));
        }

        record Forwarded(SensorReading reading, UUID equipmentId) {}
    }

    private static final class FakeDevices implements DeviceRepository {

        private final Map<String, SensorDevice> byCode = new HashMap<>();

        void save(SensorDevice device) {
            byCode.put(device.breweryId() + "|" + device.code(), device);
        }

        @Override
        public void insert(SensorDevice device) {
            save(device);
        }

        @Override
        public boolean updateStatus(SensorDevice device, long expectedVersion) {
            save(device);
            return true;
        }

        @Override
        public Optional<SensorDevice> byCode(UUID breweryId, String code) {
            return Optional.ofNullable(byCode.get(breweryId + "|" + code));
        }

        @Override
        public Optional<SensorDevice> byId(UUID breweryId, UUID deviceId) {
            return byCode.values().stream()
                    .filter(d -> d.breweryId().equals(breweryId) && d.id().equals(deviceId))
                    .findFirst();
        }

        @Override
        public List<SensorDevice> findAll(UUID breweryId) {
            return byCode.values().stream().filter(d -> d.breweryId().equals(breweryId)).toList();
        }
    }

    /** Dublê que imita a restrição única {@code (device_id, message_id)}. */
    private static final class FakeReadings implements ReadingRepository {

        private final List<SensorReading> stored = new ArrayList<>();

        @Override
        public boolean insertIfAbsent(SensorReading reading) {
            var exists = stored.stream().anyMatch(r -> r.deviceId().equals(reading.deviceId())
                    && r.messageId().equals(reading.messageId()));
            if (exists) {
                return false;
            }
            stored.add(reading);
            return true;
        }

        @Override
        public Optional<SensorReading> byMessageId(UUID breweryId, UUID deviceId, String messageId) {
            return stored.stream().filter(r -> r.breweryId().equals(breweryId)
                    && r.deviceId().equals(deviceId) && r.messageId().equals(messageId)).findFirst();
        }

        @Override
        public List<SensorReading> inWindow(UUID breweryId, UUID deviceId, Instant from, Instant to,
                int limit) {
            return stored.stream()
                    .filter(r -> r.breweryId().equals(breweryId) && r.deviceId().equals(deviceId))
                    .filter(r -> !r.measuredAt().isBefore(from) && r.measuredAt().isBefore(to))
                    .limit(limit)
                    .toList();
        }
    }
}
