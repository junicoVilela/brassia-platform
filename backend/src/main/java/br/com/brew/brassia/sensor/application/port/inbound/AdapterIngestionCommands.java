package br.com.brew.brassia.sensor.application.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ingestão de payload no formato do fabricante (INT-006). */
public interface AdapterIngestionCommands {

    Result ingest(Request request);

    /**
     * @param deviceCode vem da URL, não do payload. O {@code deviceId} de dentro da mensagem é informação
     *                   do fabricante e serve para conferência — deixá-lo escolher o dispositivo permitiria
     *                   a um gateway gravar na série de outro aparelho da mesma cervejaria.
     * @param payload    o corpo como o aparelho mandou, sem interpretação prévia.
     */
    record Request(UUID breweryId, String deviceCode, Map<String, Object> payload) {
    }

    record Result(List<ReadingCommands.Result> readings) {
    }
}
