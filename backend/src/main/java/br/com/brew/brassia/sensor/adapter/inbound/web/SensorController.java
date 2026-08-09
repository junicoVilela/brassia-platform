package br.com.brew.brassia.sensor.adapter.inbound.web;

import br.com.brew.brassia.sensor.adapter.inbound.web.dto.SensorDtos;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceCommands;
import br.com.brew.brassia.sensor.application.port.inbound.DeviceStatusCommands;
import br.com.brew.brassia.sensor.application.port.inbound.ReadingCommands;
import br.com.brew.brassia.sensor.application.port.inbound.SensorQueries;
import br.com.brew.brassia.sensor.domain.DeviceStatus;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sensores vistos de fora (INT-001). */
@RestController
@RequestMapping("/api/v1/sensors")
final class SensorController {

    private static final int DEFAULT_LIMIT = 500;

    private final DeviceCommands register;
    private final DeviceStatusCommands status;
    private final ReadingCommands ingestion;
    private final SensorQueries queries;
    private final Clock clock;

    SensorController(DeviceCommands register, DeviceStatusCommands status, ReadingCommands ingestion,
            SensorQueries queries) {
        this.register = register;
        this.status = status;
        this.ingestion = ingestion;
        this.queries = queries;
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/devices")
    List<SensorDtos.DeviceView> devices(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("sensor.reading.read");
        return SensorDtos.DeviceView.from(queries.devices(principal.requireBrewery()));
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    SensorDtos.DeviceView registerDevice(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SensorDtos.RegisterDeviceRequest request) {
        principal.requirePermission("sensor.device.manage");
        return SensorDtos.DeviceView.from(register.register(new DeviceCommands.Request(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.measure(), request.unit(), request.equipmentId(),
                request.expectedIntervalSeconds() == null
                        ? null : Duration.ofSeconds(request.expectedIntervalSeconds()))));
    }

    /**
     * Muda o estado do dispositivo.
     *
     * <p><strong>A alçada depende do destino, e por isso a verificação está aqui e não numa anotação.</strong>
     * Pausar é operação de manutenção; revogar diz que aquela identidade não é mais confiável e que a série
     * dela passa a ser suspeita. Exigir a mesma permissão para as duas faria de "revogar" um efeito
     * colateral acessível a quem só precisava parar o sensor para trocar a bateria.
     */
    @PostMapping("/devices/{deviceId}/status")
    SensorDtos.DeviceView changeStatus(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID deviceId,
            @Valid @RequestBody SensorDtos.ChangeStatusRequest request) {
        var target = parseStatus(request.status());
        principal.requirePermission(target == DeviceStatus.REVOKED
                ? "sensor.device.revoke" : "sensor.device.manage");
        return SensorDtos.DeviceView.from(status.changeStatus(new DeviceStatusCommands.Request(
                principal.userId(), principal.requireBrewery(), deviceId, target,
                request.expectedVersion())));
    }

    /**
     * Recebe uma leitura.
     *
     * <p><strong>201 para nova, 200 para repetida.</strong> A distinção é a resposta certa a um reenvio: o
     * dispositivo que não recebeu o ACK e tentou de novo fez a coisa certa, e responder erro o ensinaria a
     * continuar tentando. 200 diz "está registrado, pode parar" sem criar uma segunda linha.
     */
    @PostMapping("/readings")
    ResponseEntity<SensorDtos.IngestResponse> ingest(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SensorDtos.IngestRequest request) {
        principal.requirePermission("sensor.reading.ingest");
        var result = ingestion.ingest(new ReadingCommands.Request(
                principal.requireBrewery(), request.deviceCode(), request.messageId(), request.measure(),
                request.value(), request.unit(), request.measuredAt()));
        var body = SensorDtos.IngestResponse.from(result.reading(), result.duplicate());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    /**
     * Leituras de um dispositivo numa janela.
     *
     * <p>A janela padrão é de 24 h para trás. Não é preguiça de quem chama: uma série de sensor não tem
     * fim, e um endpoint sem recorte padrão é um endpoint que fica lento em produção três meses depois de
     * entrar no ar.
     */
    @GetMapping("/devices/{deviceId}/readings")
    List<SensorDtos.ReadingView> readings(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID deviceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit) {
        principal.requirePermission("sensor.reading.read");
        var end = to == null ? clock.instant() : to;
        var start = from == null ? end.minus(Duration.ofDays(1)) : from;
        return SensorDtos.ReadingView.from(queries.readings(principal.requireBrewery(), deviceId,
                start, end, limit == null ? DEFAULT_LIMIT : limit));
    }

    private static DeviceStatus parseStatus(String raw) {
        try {
            return DeviceStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("estado inválido: " + raw);
        }
    }
}
