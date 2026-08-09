package br.com.brew.brassia.sensor.adapter.inbound.web.dto;

import br.com.brew.brassia.sensor.domain.SensorDevice;
import br.com.brew.brassia.sensor.domain.SensorReading;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP dos sensores (INT-001). */
public final class SensorDtos {

    private SensorDtos() {
    }

    public record RegisterDeviceRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank String measure,
            @NotBlank String unit,
            UUID equipmentId,
            @Positive Integer expectedIntervalSeconds,
            /**
             * De que jeito o dispositivo fala (INT-006). Ausente = formato da casa.
             *
             * <p>É atributo do cadastro e não da mensagem: deixar o payload declarar o próprio formato
             * seria confiar num campo que o firmware preenche.
             */
            String payloadFormat) {
    }

    public record ChangeStatusRequest(
            @NotBlank String status,
            @NotNull Long expectedVersion) {
    }

    /**
     * A mensagem do dispositivo.
     *
     * <p>Não há {@code receivedAt} aqui, e é deliberado: o instante de recebimento é <em>nosso</em>, e
     * aceitá-lo do dispositivo permitiria a ele mascarar o próprio atraso — que é justamente o que a
     * história pede para sinalizar.
     */
    public record IngestRequest(
            @NotBlank @Size(max = 40) String deviceCode,
            @NotBlank @Size(max = 80) String messageId,
            @NotBlank String measure,
            @NotNull BigDecimal value,
            @NotBlank String unit,
            @NotNull Instant measuredAt) {
    }

    public record DeviceView(
            UUID id,
            String code,
            String name,
            String measure,
            String unit,
            UUID equipmentId,
            Long expectedIntervalSeconds,
            String payloadFormat,
            String status,
            Instant registeredAt,
            long version) {

        public static DeviceView from(SensorDevice device) {
            return new DeviceView(device.id(), device.code(), device.name(), device.measure().name(),
                    device.unit(), device.equipmentId(),
                    device.expectedInterval() == null ? null : device.expectedInterval().toSeconds(),
                    device.payloadFormat().name(),
                    device.status().name(), device.registeredAt(), device.version());
        }

        public static List<DeviceView> from(List<SensorDevice> devices) {
            return devices.stream().map(DeviceView::from).toList();
        }
    }

    public record ReadingView(
            UUID id,
            UUID deviceId,
            String messageId,
            String measure,
            BigDecimal value,
            String unit,
            Instant measuredAt,
            Instant receivedAt,
            String quality,
            String qualityReason,
            long delaySeconds,
            boolean late) {

        public static ReadingView from(SensorReading reading) {
            return new ReadingView(reading.id(), reading.deviceId(), reading.messageId(),
                    reading.measure().name(), reading.value(), reading.unit(), reading.measuredAt(),
                    reading.receivedAt(), reading.quality().name(), reading.qualityReason(),
                    reading.lateness().delay().toSeconds(), reading.lateness().late());
        }

        public static List<ReadingView> from(List<SensorReading> readings) {
            return readings.stream().map(ReadingView::from).toList();
        }
    }

    /**
     * A resposta da ingestão por adapter (INT-006).
     *
     * <p>Lista, porque uma mensagem de fabricante pode trazer mais de uma grandeza e virar mais de uma
     * leitura. `duplicate` por leitura, não pela mensagem: reenviar uma mensagem cuja densidade já foi
     * gravada mas cuja temperatura falhou grava só a que faltava.
     */
    public record AdapterIngestResponse(List<IngestResponse> readings) {

        public static AdapterIngestResponse from(
                br.com.brew.brassia.sensor.application.port.inbound.AdapterIngestionCommands.Result result) {
            return new AdapterIngestResponse(result.readings().stream()
                    .map(r -> IngestResponse.from(r.reading(), r.duplicate()))
                    .toList());
        }
    }

    /**
     * A resposta da ingestão.
     *
     * <p>{@code duplicate} viaja até o cliente porque é o que permite a quem opera distinguir "o gateway
     * está reenviando demais" de "estou recebendo o dobro de leituras" — duas causas com o mesmo sintoma no
     * gráfico e soluções completamente diferentes.
     */
    public record IngestResponse(ReadingView reading, boolean duplicate) {

        public static IngestResponse from(SensorReading reading, boolean duplicate) {
            return new IngestResponse(ReadingView.from(reading), duplicate);
        }
    }
}
