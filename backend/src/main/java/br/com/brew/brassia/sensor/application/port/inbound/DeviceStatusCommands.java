package br.com.brew.brassia.sensor.application.port.inbound;

import br.com.brew.brassia.sensor.domain.DeviceStatus;
import br.com.brew.brassia.sensor.domain.SensorDevice;
import java.util.UUID;

/**
 * Pausar, retomar e revogar dispositivo (INT-001).
 *
 * <p>Separada de {@link DeviceCommands} porque são alçadas distintas: cadastrar é ato de instalação,
 * revogar é ato de confiança — quem instala um sensor não necessariamente decide que a série dele deixou de
 * valer. As permissões exigidas são diferentes e o controller as verifica separadamente.
 */
public interface DeviceStatusCommands {

    SensorDevice changeStatus(Request request);

    record Request(
            UUID actorId,
            UUID breweryId,
            UUID deviceId,
            DeviceStatus target,
            long expectedVersion) {
    }
}
