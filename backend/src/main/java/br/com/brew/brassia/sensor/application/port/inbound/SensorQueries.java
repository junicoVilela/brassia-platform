package br.com.brew.brassia.sensor.application.port.inbound;

import br.com.brew.brassia.sensor.domain.SensorDevice;
import br.com.brew.brassia.sensor.domain.SensorReading;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas de dispositivos e leituras (INT-001). */
public interface SensorQueries {

    List<SensorDevice> devices(UUID breweryId);

    /**
     * Leituras de um dispositivo numa janela.
     *
     * <p>A janela é obrigatória porque uma série de sensor não tem fim: um dispositivo de 30 segundos
     * produz 2.880 linhas por dia, e uma consulta sem recorte é uma consulta que fica lenta em produção
     * três meses depois de entrar no ar.
     */
    List<SensorReading> readings(UUID breweryId, UUID deviceId, Instant from, Instant to, int limit);
}
