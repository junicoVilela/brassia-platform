package br.com.brew.brassia.sensor.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * De que jeito um dispositivo fala (INT-006).
 *
 * <p><strong>O formato é atributo do dispositivo, não da mensagem.</strong> Deixar o payload declarar o
 * próprio formato seria confiar num campo que o firmware preenche — e um firmware atualizado que mudasse a
 * declaração passaria a ser interpretado de outro jeito sem que ninguém decidisse isso. Aqui a mensagem que
 * não corresponde ao formato cadastrado é recusada.
 *
 * <p>Os formatos concretos existem porque os aparelhos reais desta escala falam assim: um iSpindel manda
 * gravidade em graus Plato ou como polinômio de inclinação, um Tilt manda em SG e Fahrenheit. Traduzir na
 * borda é o que impede essas peculiaridades de entrarem no domínio.
 */
public enum PayloadFormat {

    /** O formato da casa: {@code contracts/sensor-reading.schema.json}. */
    CANONICAL {
        @Override
        public CanonicalReading translate(Map<String, Object> payload) {
            return new CanonicalReading(
                    text(payload, "deviceId"),
                    text(payload, "externalReadingId"),
                    instant(payload, "measuredAt"),
                    decimal(payload, "temperatureC"),
                    decimal(payload, "specificGravity"),
                    decimal(payload, "pressureKpa"));
        }
    },

    /**
     * iSpindel: densímetro flutuante de código aberto, muito comum nesta escala.
     *
     * <p>Manda {@code name}, {@code ID}, {@code temperature} (°C), {@code gravity} (SG) e {@code interval}.
     * Não manda instante nenhum — o aparelho não tem relógio confiável —, e é por isso que
     * {@code measuredAt} vem do momento em que a mensagem chega. A consequência é declarada: para um
     * iSpindel, o atraso medido por INT-001 é sempre próximo de zero, porque os dois relógios são o mesmo.
     */
    ISPINDEL {
        @Override
        public CanonicalReading translate(Map<String, Object> payload) {
            var device = text(payload, "name");
            var reading = optionalText(payload, "ID");
            var at = Instant.now();
            return new CanonicalReading(
                    device,
                    // Sem identificador de mensagem próprio, a identidade vem do aparelho mais o instante
                    // de chegada. Duas mensagens no mesmo segundo do mesmo aparelho seriam a mesma leitura
                    // — o que é aceitável porque o intervalo de um iSpindel é de minutos, nunca de
                    // milissegundos.
                    (reading == null ? device : reading) + "@" + at.getEpochSecond(),
                    at,
                    decimal(payload, "temperature"),
                    decimal(payload, "gravity"),
                    null);
        }
    },

    /**
     * Tilt: hidrômetro Bluetooth. Reporta em Fahrenheit e SG.
     *
     * <p>A conversão de °F para °C acontece aqui, na borda. Guardar Fahrenheit e converter na leitura
     * espalharia a conversão por toda consulta que tocasse a série.
     */
    TILT {
        @Override
        public CanonicalReading translate(Map<String, Object> payload) {
            var device = text(payload, "Color");
            var fahrenheit = decimal(payload, "Temp");
            var celsius = fahrenheit == null ? null
                    : fahrenheit.subtract(new BigDecimal("32"))
                            .multiply(new BigDecimal("5"))
                            .divide(new BigDecimal("9"), 2, java.math.RoundingMode.HALF_UP);
            var at = Instant.now();
            return new CanonicalReading(
                    device,
                    device + "@" + at.getEpochSecond(),
                    at,
                    celsius,
                    decimal(payload, "SG"),
                    null);
        }
    };

    /** Traduz o payload do fabricante para o formato da casa. */
    public abstract CanonicalReading translate(Map<String, Object> payload);

    public static PayloadFormat of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("formato do payload é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("formato de payload desconhecido: " + raw);
        }
    }

    // --- leitura defensiva do payload ---
    //
    // Um payload de dispositivo é entrada de terceiro: campo ausente, tipo trocado e número como texto são
    // o normal, não a exceção. Cada acessor recusa com mensagem sobre o CAMPO, porque quem configura um
    // gateway precisa saber qual campo está errado — "payload inválido" manda a pessoa adivinhar.

    static String text(Map<String, Object> payload, String field) {
        var value = optionalText(payload, field);
        if (value == null) {
            throw new IllegalArgumentException("campo obrigatório ausente: " + field);
        }
        return value;
    }

    static String optionalText(Map<String, Object> payload, String field) {
        var value = payload.get(field);
        if (value == null) {
            return null;
        }
        var text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    static Instant instant(Map<String, Object> payload, String field) {
        var text = text(payload, field);
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("campo " + field + " não é um instante ISO-8601");
        }
    }

    /**
     * Número, aceitando texto.
     *
     * <p>Aceitar {@code "18.5"} não é leniência gratuita: muitos firmwares serializam tudo como string, e
     * recusar isso rejeitaria aparelhos que funcionam perfeitamente. O que <strong>não</strong> se aceita é
     * texto que não é número — aí o campo está errado e dizer isso é melhor que gravar zero.
     */
    static BigDecimal decimal(Map<String, Object> payload, String field) {
        var value = payload.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        var text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("campo " + field + " não é numérico");
        }
    }

    /** Para o cadastro do dispositivo saber o que oferecer. */
    public static Function<PayloadFormat, String> labels() {
        return format -> switch (format) {
            case CANONICAL -> "Formato BrassIA (canônico)";
            case ISPINDEL -> "iSpindel";
            case TILT -> "Tilt";
        };
    }
}
