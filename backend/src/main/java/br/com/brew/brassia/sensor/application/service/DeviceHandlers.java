package br.com.brew.brassia.sensor.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceCommands;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceStatusCommands;
import br.com.brew.brassia.sensor.application.port.inbound.SensorQueries;
import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.SensorDevice;
import br.com.brew.brassia.sensor.domain.SensorReading;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cadastro, estado e consulta de dispositivos (INT-001).
 *
 * <p>Aqui <strong>tudo</strong> é auditado, ao contrário da ingestão. A diferença não é de importância e
 * sim de natureza: cadastrar um dispositivo, pausá-lo ou revogá-lo muda o que a série histórica significa —
 * uma lacuna de seis horas numa curva de fermentação é uma pergunta sem resposta até alguém descobrir que o
 * dispositivo estava pausado, e quem o pausou.
 */
public final class DeviceHandlers {

    private DeviceHandlers() {
    }

    /** Cadastro de dispositivo. */
    public static final class Register implements DeviceCommands {

        private final DeviceRepository devices;
        private final AuditTrail audit;
        private final Clock clock;

        public Register(DeviceRepository devices, AuditTrail audit, Clock clock) {
            this.devices = Objects.requireNonNull(devices, "devices");
            this.audit = Objects.requireNonNull(audit, "audit");
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        @Override
        public SensorDevice register(Request request) {
            Objects.requireNonNull(request, "request");

            var device = SensorDevice.register(request.breweryId(), request.code(), request.name(),
                    Measure.of(request.measure()), request.unit(), request.equipmentId(),
                    request.expectedInterval(), request.actorId(), clock.instant());

            // O código é único por cervejaria e quem verifica é o banco, não uma consulta daqui: duas
            // requisições simultâneas cadastrando "TANK-01" passariam as duas por uma checagem prévia.
            devices.insert(device);

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("code", device.code());
            metadata.put("measure", device.measure().name());
            metadata.put("unit", device.unit());
            metadata.put("expectedInterval", String.valueOf(device.expectedInterval()));
            if (device.equipmentId() != null) {
                metadata.put("equipmentId", device.equipmentId().toString());
            }
            audit.record(AuditEvent.success(device.breweryId(), device.registeredBy(),
                    "sensor.device.register", "sensor_device", device.id().toString(), metadata));
            return device;
        }
    }

    /** Pausar, retomar e revogar. */
    public static final class ChangeStatus implements DeviceStatusCommands {

        private final DeviceRepository devices;
        private final AuditTrail audit;

        public ChangeStatus(DeviceRepository devices, AuditTrail audit) {
            this.devices = Objects.requireNonNull(devices, "devices");
            this.audit = Objects.requireNonNull(audit, "audit");
        }

        @Override
        public SensorDevice changeStatus(Request request) {
            Objects.requireNonNull(request, "request");

            var current = devices.byId(request.breweryId(), request.deviceId())
                    .orElseThrow(() -> new UnknownDeviceException(String.valueOf(request.deviceId())));

            var changed = current.changeStatusTo(request.target());

            // Concorrência otimista: dois operadores decidindo o destino do mesmo dispositivo ao mesmo
            // tempo não podem terminar com um deles achando que revogou algo que o outro reativou.
            if (!devices.updateStatus(changed, request.expectedVersion())) {
                throw new IllegalStateException("dispositivo alterado por outra operação");
            }

            var metadata = new LinkedHashMap<String, String>();
            metadata.put("code", current.code());
            metadata.put("from", current.status().name());
            metadata.put("to", changed.status().name());
            audit.record(AuditEvent.success(changed.breweryId(), request.actorId(),
                    "sensor.device.status", "sensor_device", changed.id().toString(), metadata));
            return changed;
        }
    }

    /** Consultas. */
    public static final class Queries implements SensorQueries {

        /** Teto de leituras por consulta: acima disso é exportação, que é outra história. */
        private static final int MAX_LIMIT = 1000;

        private final DeviceRepository devices;
        private final ReadingRepository readings;

        public Queries(DeviceRepository devices, ReadingRepository readings) {
            this.devices = Objects.requireNonNull(devices, "devices");
            this.readings = Objects.requireNonNull(readings, "readings");
        }

        @Override
        public List<SensorDevice> devices(UUID breweryId) {
            return devices.findAll(Objects.requireNonNull(breweryId, "breweryId"));
        }

        @Override
        public List<SensorReading> readings(UUID breweryId, UUID deviceId, Instant from, Instant to,
                int limit) {
            Objects.requireNonNull(breweryId, "breweryId");
            Objects.requireNonNull(deviceId, "deviceId");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (!from.isBefore(to)) {
                throw new IllegalArgumentException("janela inválida: início deve preceder o fim");
            }

            // O dispositivo é resolvido dentro da cervejaria antes de consultar as leituras. Sem isso, um
            // id de dispositivo de outra cervejaria devolveria lista vazia — que é a resposta de "não há
            // leituras", não a de "este dispositivo não é seu".
            devices.byId(breweryId, deviceId)
                    .orElseThrow(() -> new UnknownDeviceException(String.valueOf(deviceId)));

            return readings.inWindow(breweryId, deviceId, from, to, Math.min(Math.max(limit, 1), MAX_LIMIT));
        }
    }
}
