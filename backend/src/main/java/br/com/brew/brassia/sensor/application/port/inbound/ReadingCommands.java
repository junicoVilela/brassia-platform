package br.com.brew.brassia.sensor.application.port.inbound;

import br.com.brew.brassia.sensor.domain.SensorReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ingestão de leitura (INT-001). */
public interface ReadingCommands {

    Result ingest(Request request);

    /**
     * O pedido de ingestão.
     *
     * <p>O dispositivo é identificado pelo <strong>código</strong>, não pelo id interno: quem envia é um
     * aparelho configurado com uma etiqueta, e exigir dele um UUID gerado por nós obrigaria a reconfigurar
     * o firmware a cada recadastro.
     */
    record Request(
            UUID breweryId,
            String deviceCode,
            String messageId,
            String measure,
            BigDecimal value,
            String unit,
            Instant measuredAt) {
    }

    /**
     * O resultado, que distingue a leitura nova da repetida.
     *
     * <p><strong>{@code duplicate} é resposta, não erro.</strong> O dispositivo que reenviou por não ter
     * recebido o ACK fez a coisa certa; responder erro o ensinaria a continuar tentando. A distinção chega
     * até o HTTP — 201 para nova, 200 para repetida — porque é o que permite a quem opera diferenciar "o
     * gateway está reenviando demais" de "estou recebendo o dobro de leituras".
     */
    record Result(SensorReading reading, boolean duplicate) {
    }
}
