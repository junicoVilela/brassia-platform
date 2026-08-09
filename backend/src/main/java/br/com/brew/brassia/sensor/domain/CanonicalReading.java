package br.com.brew.brassia.sensor.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Uma mensagem de dispositivo já traduzida para o formato da casa (INT-006).
 *
 * <p>Espelha {@code contracts/sensor-reading.schema.json}. É a <strong>fronteira entre o mundo de fora e o
 * nosso</strong>: tudo que chega de um aparelho passa por aqui antes de virar {@link SensorReading}, e
 * nenhuma peculiaridade de fabricante atravessa esse ponto.
 *
 * <p><strong>Uma mensagem, várias grandezas.</strong> Um iSpindel reporta densidade e temperatura no mesmo
 * envio, porque mede as duas ao mesmo tempo; do nosso lado elas viram duas leituras, porque são dois fatos
 * sobre grandezas diferentes. A tradução acontece aqui, e não no dispositivo, porque o dispositivo não tem
 * como saber como nós modelamos.
 *
 * <p>A unidade é <strong>fixada pelo formato canônico</strong>, não escolhida pelo aparelho: temperatura em
 * °C, densidade em SG, pressão em kPa. Deixar cada fabricante mandar a unidade que preferir empurraria a
 * conversão para dentro do domínio, onde ela não pertence — e um firmware que mudasse de escala passaria
 * despercebido.
 */
public record CanonicalReading(
        String deviceId,
        String externalReadingId,
        Instant measuredAt,
        BigDecimal temperatureC,
        BigDecimal specificGravity,
        BigDecimal pressureKpa) {

    public CanonicalReading {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("identificador do dispositivo é obrigatório");
        }
        if (externalReadingId == null || externalReadingId.isBlank()) {
            throw new IllegalArgumentException("identificador da leitura é obrigatório");
        }
        Objects.requireNonNull(measuredAt, "instante da medição é obrigatório");
        if (temperatureC == null && specificGravity == null && pressureKpa == null) {
            // O `anyOf` do schema, aplicado aqui: uma mensagem que só traz bateria e sinal é telemetria do
            // aparelho, não medição do processo. Aceitá-la criaria linhas sem grandeza nenhuma.
            throw new IllegalArgumentException("mensagem sem nenhuma grandeza medida");
        }
    }

    /**
     * As grandezas presentes, com a unidade canônica de cada uma.
     *
     * <p>A ordem é estável (temperatura, densidade, pressão) para que o identificador derivado de cada
     * leitura — ver {@link #messageIdFor} — seja o mesmo em toda releitura da mesma mensagem.
     */
    public Map<Measure, Value> measures() {
        var values = new LinkedHashMap<Measure, Value>();
        if (temperatureC != null) {
            values.put(Measure.TEMPERATURE, new Value(temperatureC, "C"));
        }
        if (specificGravity != null) {
            values.put(Measure.DENSITY, new Value(specificGravity, "SG"));
        }
        if (pressureKpa != null) {
            // O canônico é kPa e o nosso domínio mede pressão em PSI ou bar. A conversão é feita aqui, na
            // borda, e não no domínio: 1 bar = 100 kPa, exato, sem arredondamento que invente precisão.
            values.put(Measure.PRESSURE,
                    new Value(pressureKpa.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP),
                            "BAR"));
        }
        return values;
    }

    /**
     * A identidade de cada leitura derivada desta mensagem.
     *
     * <p><strong>Deriva do identificador da mensagem, não é sorteada.</strong> É o que faz a idempotência
     * de INT-001 valer também aqui: o aparelho que reenvia a mesma mensagem produz exatamente as mesmas
     * chaves, e as leituras já gravadas são reconhecidas como repetição. Um identificador novo a cada
     * conversão criaria uma leitura nova por reenvio — e o adapter viraria o furo por onde a idempotência
     * vaza.
     *
     * <p>O sufixo com a grandeza é necessário porque uma mensagem vira várias leituras: sem ele, densidade
     * e temperatura do mesmo envio disputariam a mesma chave e uma das duas seria descartada como duplicata.
     */
    public String messageIdFor(Measure measure) {
        return externalReadingId + ":" + measure.name();
    }

    /** Valor já na unidade canônica da grandeza. */
    public record Value(BigDecimal amount, String unit) {
    }
}
