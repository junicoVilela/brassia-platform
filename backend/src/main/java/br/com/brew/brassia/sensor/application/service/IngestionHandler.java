package br.com.brew.brassia.sensor.application.service;

import br.com.brew.brassia.sensor.application.port.inbound.ReadingCommands;
import br.com.brew.brassia.sensor.application.port.outbound.BatchCurveFeed;
import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.SensorReading;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import java.time.Clock;
import java.util.Objects;

/**
 * Ingestão de uma leitura de sensor (INT-001).
 *
 * <p><strong>A idempotência é a história inteira, e ela não é uma consulta.</strong> O caminho ingênuo —
 * "procura se já existe, senão insere" — tem uma janela entre a pergunta e a escrita, e é dentro dessa
 * janela que cai o reenvio de um gateway que despachou a mesma mensagem duas vezes em milissegundos, que é
 * justamente o cenário para o qual a idempotência existe. Aqui quem decide é a restrição única do banco:
 * tenta-se inserir, e o resultado diz se entrou. Só quando não entrou é que se vai buscar a leitura que já
 * estava lá, para devolver a mesma resposta do primeiro envio.
 *
 * <p><strong>Por que a leitura não é auditada.</strong> O AGENTS.md pede auditoria para comando crítico, e
 * telemetria não é comando: um dispositivo de 30 segundos gera 2.880 linhas por dia, e auditar cada uma
 * encheria a trilha de auditoria de ruído até que ninguém mais conseguisse encontrar nela a alteração de
 * custo ou a liberação de lote que ela existe para guardar. O que <em>é</em> auditado é o que muda a
 * confiança na série: cadastrar, pausar e revogar dispositivo (ver {@link DeviceHandlers}). A leitura em si
 * é o próprio registro — imutável, com os dois relógios e a qualidade gravados.
 */
public final class IngestionHandler implements ReadingCommands {

    private final DeviceRepository devices;
    private final ReadingRepository readings;
    private final BatchCurveFeed curve;
    private final Clock clock;

    public IngestionHandler(DeviceRepository devices, ReadingRepository readings, BatchCurveFeed curve,
            Clock clock) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.readings = Objects.requireNonNull(readings, "readings");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result ingest(Request request) {
        Objects.requireNonNull(request, "request");

        var device = devices.byCode(request.breweryId(), normalize(request.deviceCode()))
                .orElseThrow(() -> new UnknownDeviceException(request.deviceCode()));

        var measure = Measure.of(request.measure());
        // Estado e correspondência com o cadastro são verificados antes de qualquer escrita: dispositivo
        // pausado e mensagem com grandeza errada são recusa, não leitura sinalizada.
        device.requireAccepts(measure, request.unit());

        var reading = SensorReading.receive(device, request.messageId(), request.value(), request.unit(),
                request.measuredAt(), clock.instant());

        if (readings.insertIfAbsent(reading)) {
            // Só a primeira gravação alimenta a curva. Encaminhar também na reentrega seria inofensivo —
            // a leitura de fermentação é idempotente pela mesma chave natural — mas seria trabalho por
            // engano num caminho que existe justamente para ser barato: o reenvio em rajada.
            curve.forward(reading, device.equipmentId());
            return new Result(reading, false);
        }

        // Já existia. Devolvemos a leitura GRAVADA, não a que acabamos de montar: elas diferem no
        // `receivedAt` e no id, e responder a segunda faria o dispositivo acreditar que a medição chegou
        // agora — apagando o atraso real do primeiro envio, que é o dado que interessa a quem investiga.
        var stored = readings.byMessageId(reading.breweryId(), device.id(), reading.messageId())
                .orElseThrow(() -> new IllegalStateException(
                        "leitura duplicada sem original: " + reading.messageId()));
        return new Result(stored, true);
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("código do dispositivo é obrigatório");
        }
        return code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
