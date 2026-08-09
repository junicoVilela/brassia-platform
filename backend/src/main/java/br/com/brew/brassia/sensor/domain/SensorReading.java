package br.com.brew.brassia.sensor.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma leitura recebida de um dispositivo (INT-001).
 *
 * <p><strong>Dois relógios, e os dois ficam gravados.</strong> {@code measuredAt} é o relógio do
 * dispositivo e responde "quando isso aconteceu"; {@code receivedAt} é o nosso e responde "quando ficamos
 * sabendo". Guardar só um destrói informação que não se recupera: com apenas o nosso, um lote de leituras
 * represado por queda de rede vira uma rajada de medições simultâneas que nunca existiu; com apenas o
 * dele, um relógio errado reescreve a história e ninguém consegue perceber, porque não sobrou nada com que
 * comparar.
 *
 * <p>Leitura é <strong>imutável</strong> — é medição, e o AGENTS.md põe medição entre o que não se apaga
 * nem se reescreve. Um sensor que se corrige manda outra leitura; a anterior continua sendo o que ele
 * disse na hora.
 */
public final class SensorReading {

    private final UUID id;
    private final UUID breweryId;
    private final UUID deviceId;
    private final String messageId;
    private final Measure measure;
    private final BigDecimal value;
    private final String unit;
    private final Instant measuredAt;
    private final Instant receivedAt;
    private final ReadingQuality quality;
    private final String qualityReason;
    private final Lateness lateness;

    private SensorReading(UUID id, UUID breweryId, UUID deviceId, String messageId, Measure measure,
            BigDecimal value, String unit, Instant measuredAt, Instant receivedAt, ReadingQuality quality,
            String qualityReason, Lateness lateness) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.measure = Objects.requireNonNull(measure, "measure");
        this.value = Objects.requireNonNull(value, "value");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.measuredAt = Objects.requireNonNull(measuredAt, "measuredAt");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.qualityReason = qualityReason;
        this.lateness = Objects.requireNonNull(lateness, "lateness");
    }

    /**
     * Recebe uma leitura, derivando qualidade e atraso.
     *
     * <p>O device já recusou o que não corresponde ao cadastro ({@link SensorDevice#requireAccepts}); o que
     * chega aqui é medição do que se esperava, e o que resta é dizer se dá para acreditar nela.
     *
     * <p><strong>Relógio adiantado tem precedência sobre faixa.</strong> Quando o instante não é confiável,
     * dizer que o valor está fora da faixa seria responder a pergunta errada — o problema daquela leitura
     * não é o número, é que ela não pode ser posicionada na série. Um dispositivo que volta de reset com o
     * relógio de fábrica manda valores perfeitos com instante impossível, e é o instante que precisa
     * aparecer para quem for investigar.
     */
    public static SensorReading receive(SensorDevice device, String messageId, BigDecimal value,
            String rawUnit, Instant measuredAt, Instant receivedAt) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(value, "valor é obrigatório");
        Objects.requireNonNull(measuredAt, "instante da medição é obrigatório");
        Objects.requireNonNull(receivedAt, "instante de recebimento é obrigatório");
        var key = requireMessageId(messageId);
        var unit = device.measure().requireUnit(rawUnit);

        ReadingQuality quality;
        String reason;
        if (measuredAt.isAfter(receivedAt)) {
            quality = ReadingQuality.FUTURE_CLOCK;
            reason = "medição datada no futuro em relação ao recebimento";
        } else if (device.measure().isImplausible(value, unit)) {
            quality = ReadingQuality.OUT_OF_RANGE;
            reason = device.measure() + " " + value.toPlainString() + " " + unit
                    + " fora da faixa plausível " + device.measure().rangeOf(unit);
        } else {
            quality = ReadingQuality.GOOD;
            reason = null;
        }

        return new SensorReading(UUID.randomUUID(), device.breweryId(), device.id(), key,
                device.measure(), value, unit, measuredAt, receivedAt, quality, reason,
                Lateness.between(measuredAt, receivedAt, device.expectedInterval()));
    }

    /**
     * Identidade da mensagem, que é o que torna a repetição reconhecível.
     *
     * <p><strong>Por que a chave vem do dispositivo e não é derivada do conteúdo.</strong> Um hash do
     * payload trataria duas medições legitimamente idênticas — mesma temperatura, mesmo segundo, sensor
     * parado — como a mesma mensagem, e descartaria uma leitura verdadeira. A repetição que precisamos
     * reconhecer é a de <em>transporte</em>: o dispositivo não recebeu o ACK e reenviou. Só ele sabe que é
     * o mesmo envio, e é por isso que ele carimba.
     */
    private static String requireMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("identificador da mensagem é obrigatório");
        }
        var key = messageId.trim();
        if (key.length() > 80) {
            throw new IllegalArgumentException("identificador da mensagem excede 80 caracteres");
        }
        return key;
    }

    public static SensorReading reconstitute(UUID id, UUID breweryId, UUID deviceId, String messageId,
            Measure measure, BigDecimal value, String unit, Instant measuredAt, Instant receivedAt,
            ReadingQuality quality, String qualityReason, Lateness lateness) {
        return new SensorReading(id, breweryId, deviceId, messageId, measure, value, unit, measuredAt,
                receivedAt, quality, qualityReason, lateness);
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public UUID deviceId() { return deviceId; }
    public String messageId() { return messageId; }
    public Measure measure() { return measure; }
    public BigDecimal value() { return value; }
    public String unit() { return unit; }
    public Instant measuredAt() { return measuredAt; }
    public Instant receivedAt() { return receivedAt; }
    public ReadingQuality quality() { return quality; }
    public String qualityReason() { return qualityReason; }
    public Lateness lateness() { return lateness; }
}
