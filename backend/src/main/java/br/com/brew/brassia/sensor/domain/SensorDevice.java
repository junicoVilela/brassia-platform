package br.com.brew.brassia.sensor.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Dispositivo que reporta leituras (INT-001).
 *
 * <p>O dispositivo é quem define <strong>o que</strong> se espera receber: a grandeza, a unidade e a
 * frequência. Deixar isso na mensagem seria confiar no que o dispositivo diz sobre si mesmo a cada envio, e
 * um firmware atualizado que passasse a mandar Fahrenheit sem avisar trocaria a série histórica inteira de
 * escala sem nenhum sinal. Aqui a mensagem que discorda do cadastro é recusada, não convertida.
 *
 * <p>A frequência esperada é o que dá sentido a "atrasado": sem ela, atraso é um número sem régua. Ver
 * {@link Lateness}.
 */
public final class SensorDevice {

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final String name;
    private final Measure measure;
    private final String unit;
    private final UUID equipmentId;
    private final Duration expectedInterval;
    private final PayloadFormat payloadFormat;
    private final DeviceStatus status;
    private final UUID registeredBy;
    private final Instant registeredAt;
    private final long version;

    private SensorDevice(UUID id, UUID breweryId, String code, String name, Measure measure, String unit,
            UUID equipmentId, Duration expectedInterval, PayloadFormat payloadFormat, DeviceStatus status,
            UUID registeredBy, Instant registeredAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.measure = Objects.requireNonNull(measure, "measure");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.equipmentId = equipmentId;
        this.expectedInterval = expectedInterval;
        this.payloadFormat = Objects.requireNonNull(payloadFormat, "payloadFormat");
        this.status = Objects.requireNonNull(status, "status");
        this.registeredBy = Objects.requireNonNull(registeredBy, "registeredBy");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.version = version;
    }

    /** Cadastra um dispositivo. O código é normalizado porque é a identidade externa dele. */
    public static SensorDevice register(UUID breweryId, String rawCode, String name, Measure measure,
            String rawUnit, UUID equipmentId, Duration expectedInterval, UUID actorId, Instant now) {
        return register(breweryId, rawCode, name, measure, rawUnit, equipmentId, expectedInterval,
                PayloadFormat.CANONICAL, actorId, now);
    }

    /** Cadastra declarando de que jeito o dispositivo fala (INT-006). */
    public static SensorDevice register(UUID breweryId, String rawCode, String name, Measure measure,
            String rawUnit, UUID equipmentId, Duration expectedInterval, PayloadFormat payloadFormat,
            UUID actorId, Instant now) {
        var code = normalizeCode(rawCode);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome do dispositivo é obrigatório");
        }
        Objects.requireNonNull(measure, "grandeza é obrigatória");
        var unit = measure.requireUnit(rawUnit);
        if (expectedInterval != null
                && (expectedInterval.isNegative() || expectedInterval.isZero())) {
            throw new IllegalArgumentException("intervalo esperado deve ser positivo");
        }
        return new SensorDevice(UUID.randomUUID(), breweryId, code, name.trim(), measure, unit, equipmentId,
                expectedInterval, payloadFormat == null ? PayloadFormat.CANONICAL : payloadFormat,
                DeviceStatus.ACTIVE, actorId, now, 0L);
    }

    /**
     * Código do dispositivo: maiúsculas, sem espaço nas pontas.
     *
     * <p>Normalizar não é estética. O código chega de firmware, de etiqueta colada no tanque e de digitação
     * humana, e {@code "ispindel-01"} e {@code "ISPINDEL-01"} são o mesmo aparelho para quem instalou. Sem
     * normalização vira dois cadastros, duas séries e nenhuma completa.
     */
    private static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("código do dispositivo é obrigatório");
        }
        var code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9._-]{1,39}")) {
            throw new IllegalArgumentException("código inválido: " + rawCode);
        }
        return code;
    }

    public static SensorDevice reconstitute(UUID id, UUID breweryId, String code, String name, Measure measure,
            String unit, UUID equipmentId, Duration expectedInterval, PayloadFormat payloadFormat,
            DeviceStatus status, UUID registeredBy, Instant registeredAt, long version) {
        return new SensorDevice(id, breweryId, code, name, measure, unit, equipmentId, expectedInterval,
                payloadFormat, status, registeredBy, registeredAt, version);
    }

    /** Muda o estado operacional. Revogado é terminal. */
    public SensorDevice changeStatusTo(DeviceStatus target) {
        Objects.requireNonNull(target, "estado é obrigatório");
        if (status == DeviceStatus.REVOKED) {
            throw new IllegalStateException("dispositivo revogado não volta a operar");
        }
        if (status == target) {
            throw new IllegalStateException("dispositivo já está em " + target);
        }
        return new SensorDevice(id, breweryId, code, name, measure, unit, equipmentId, expectedInterval,
                payloadFormat, target, registeredBy, registeredAt, version);
    }

    /**
     * Recusa a mensagem que não corresponde ao cadastro.
     *
     * <p>Grandeza e unidade divergentes são erro de configuração, não medição ruim — e a diferença decide o
     * tratamento. Uma temperatura absurda é sinalizada e guardada porque o instante e o dispositivo são
     * fatos; uma leitura que diz ser pressão vinda de um termômetro cadastrado não descreve fato nenhum, e
     * guardá-la sinalizada só contaminaria a série com uma linha que ninguém sabe ler.
     */
    public void requireAccepts(Measure incoming, String incomingUnit) {
        if (!status.acceptsReadings()) {
            throw new InactiveDeviceException(code, status);
        }
        if (measure != incoming) {
            throw new IllegalArgumentException(
                    "dispositivo " + code + " mede " + measure + ", não " + incoming);
        }
        var normalized = measure.requireUnit(incomingUnit);
        if (!unit.equals(normalized)) {
            throw new IllegalArgumentException(
                    "dispositivo " + code + " reporta em " + unit + ", não em " + normalized);
        }
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public String name() { return name; }
    public Measure measure() { return measure; }
    public String unit() { return unit; }
    public UUID equipmentId() { return equipmentId; }
    public Duration expectedInterval() { return expectedInterval; }
    public PayloadFormat payloadFormat() { return payloadFormat; }
    public DeviceStatus status() { return status; }
    public UUID registeredBy() { return registeredBy; }
    public Instant registeredAt() { return registeredAt; }
    public long version() { return version; }
}
