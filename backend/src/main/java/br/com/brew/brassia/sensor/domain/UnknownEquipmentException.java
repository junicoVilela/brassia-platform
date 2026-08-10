package br.com.brew.brassia.sensor.domain;

import java.util.UUID;

/**
 * Equipamento informado no cadastro do dispositivo não existe nesta cervejaria (OBS-INT-001).
 *
 * <p><strong>Existe porque a alternativa era um 500.</strong> {@code sensor_device.equipment_id} tem chave
 * estrangeira para {@code equipment}, e sem esta verificação a violação subia crua: quem integra pelo API
 * lia "erro do servidor" para um problema que está no dado que ele mandou, e não tinha como saber qual
 * campo consertar.
 *
 * <p>Descoberto ao escrever o IT da jornada de telemetria, semeando um id de equipamento aleatório.
 *
 * <p><strong>Não distingue "não existe" de "é de outra cervejaria"</strong>, pela mesma razão que
 * {@link UnknownDeviceException}: responder "existe, mas não é seu" contaria a quem pergunta que aquele id
 * é usado por alguém.
 */
public final class UnknownEquipmentException extends RuntimeException {

    private final UUID equipmentId;

    public UnknownEquipmentException(UUID equipmentId) {
        super("equipamento inexistente: " + equipmentId);
        this.equipmentId = equipmentId;
    }

    public UUID equipmentId() {
        return equipmentId;
    }
}
