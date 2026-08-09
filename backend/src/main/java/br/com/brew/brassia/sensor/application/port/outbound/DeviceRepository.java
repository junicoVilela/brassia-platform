package br.com.brew.brassia.sensor.application.port.outbound;

import br.com.brew.brassia.sensor.domain.SensorDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos dispositivos (INT-001). */
public interface DeviceRepository {

    void insert(SensorDevice device);

    /**
     * Grava o novo estado exigindo a versão esperada.
     *
     * @return {@code false} quando a versão não bate — outro alguém mexeu no dispositivo no meio-tempo.
     */
    boolean updateStatus(SensorDevice device, long expectedVersion);

    Optional<SensorDevice> byCode(UUID breweryId, String code);

    Optional<SensorDevice> byId(UUID breweryId, UUID deviceId);

    List<SensorDevice> findAll(UUID breweryId);
}
