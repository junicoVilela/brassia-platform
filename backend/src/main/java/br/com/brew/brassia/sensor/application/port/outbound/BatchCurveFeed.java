package br.com.brew.brassia.sensor.application.port.outbound;

import br.com.brew.brassia.sensor.domain.SensorReading;
import java.util.UUID;

/**
 * Para onde a telemetria vira curva de fermentação (INT-001 / DEB-INT-001).
 *
 * <p>É porta de saída e não chamada direta porque o sensor não deve conhecer a fermentação: ele sabe que
 * uma leitura pode alimentar a curva de um lote, não quem guarda essa curva. A tradução do vocabulário —
 * {@code Measure} do dispositivo para a grandeza do lote — mora no adapter, que é o único lugar onde as
 * duas linguagens legitimamente se encontram.
 */
public interface BatchCurveFeed {

    /**
     * Encaminha a leitura ao lote que ocupa o equipamento, se houver um.
     *
     * <p>Não fazer nada é o caso comum, não a exceção: dispositivo sem equipamento vinculado, tanque vazio
     * entre lotes, grandeza que não existe no vocabulário do lote. Nenhum é erro.
     *
     * @param equipmentId equipamento do dispositivo; {@code null} quando ele não tem vínculo
     */
    void forward(SensorReading reading, UUID equipmentId);
}
