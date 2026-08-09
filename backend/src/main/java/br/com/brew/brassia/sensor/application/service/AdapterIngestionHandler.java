package br.com.brew.brassia.sensor.application.service;

import br.com.brew.brassia.sensor.application.port.inbound.AdapterIngestionCommands;
import br.com.brew.brassia.sensor.application.port.inbound.ReadingCommands;
import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.domain.CanonicalReading;
import br.com.brew.brassia.sensor.domain.UnknownDeviceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Recebe o payload de um dispositivo no formato dele (INT-006).
 *
 * <p><strong>Este caso de uso traduz e delega; ele não grava.</strong> A gravação é a de INT-001, com a
 * idempotência e a sinalização de qualidade e atraso que já estavam lá. Reimplementá-las aqui criaria uma
 * segunda forma de gravar leitura — e duas formas divergem: uma ganharia uma regra que a outra não tem, e o
 * caminho por onde a leitura entrou passaria a mudar o que ela significa.
 *
 * <p><strong>Uma mensagem vira várias leituras, e a idempotência sobrevive a isso.</strong> Um iSpindel
 * reporta densidade e temperatura no mesmo envio; as chaves de cada leitura derivam do identificador da
 * mensagem ({@link CanonicalReading#messageIdFor}), então reenviar a mensagem inteira reconhece as duas
 * como repetição. Uma chave sorteada por leitura faria do adapter o furo por onde a idempotência vaza.
 *
 * <p><strong>O dispositivo é resolvido pelo código da URL, não pelo que o payload diz ser.</strong> O
 * {@code deviceId} de dentro da mensagem é informação do fabricante e serve para conferência; deixar que
 * ele escolhesse o dispositivo permitiria a um gateway gravar na série de outro aparelho da mesma
 * cervejaria.
 */
public final class AdapterIngestionHandler implements AdapterIngestionCommands {

    private final DeviceRepository devices;
    private final ReadingCommands ingestion;

    public AdapterIngestionHandler(DeviceRepository devices, ReadingCommands ingestion) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.ingestion = Objects.requireNonNull(ingestion, "ingestion");
    }

    @Override
    public Result ingest(Request request) {
        Objects.requireNonNull(request, "request");

        var code = normalize(request.deviceCode());
        var device = devices.byCode(request.breweryId(), code)
                .orElseThrow(() -> new UnknownDeviceException(request.deviceCode()));

        var canonical = device.payloadFormat().translate(request.payload());

        // Só a grandeza que o dispositivo está cadastrado para medir. Um iSpindel manda densidade e
        // temperatura no mesmo envio, e um dispositivo cadastrado como termômetro não deve começar a
        // gravar densidade porque o firmware passou a incluí-la — a série mudaria de assunto sozinha.
        var value = canonical.measures().get(device.measure());
        if (value == null) {
            throw new IllegalArgumentException(
                    "a mensagem não traz " + device.measure() + ", que é o que este dispositivo mede");
        }

        var results = new ArrayList<ReadingCommands.Result>();
        results.add(ingestion.ingest(new ReadingCommands.Request(
                request.breweryId(), code, canonical.messageIdFor(device.measure()),
                device.measure().name(), value.amount(), value.unit(), canonical.measuredAt())));

        return new Result(List.copyOf(results));
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("código do dispositivo é obrigatório");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
