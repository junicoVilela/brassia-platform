package br.com.brew.brassia.sensor.application.port.inbound;

import br.com.brew.brassia.sensor.domain.SensorDevice;
import java.time.Duration;
import java.util.UUID;

/** Cadastro de dispositivo (INT-001). */
public interface DeviceCommands {

    SensorDevice register(Request request);

    record Request(
            UUID actorId,
            UUID breweryId,
            String code,
            String name,
            String measure,
            String unit,
            UUID equipmentId,
            Duration expectedInterval) {
    }
}
