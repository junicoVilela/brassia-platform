package br.com.brew.brassia.sensor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tradução do payload de cada fabricante (INT-006).
 *
 * <p>O que estes testes fixam é a fronteira: nenhuma peculiaridade de aparelho atravessa o adapter. Fora
 * daqui, o domínio só conhece o formato da casa.
 */
class PayloadFormatTest {

    @Test
    @DisplayName("canônico: traduz o formato da casa sem conversão")
    void canonico() {
        var reading = PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "TANQUE-01",
                "externalReadingId", "msg-1",
                "measuredAt", "2026-08-09T10:00:00Z",
                "temperatureC", 18.5,
                "specificGravity", 1.048));

        assertThat(reading.deviceId()).isEqualTo("TANQUE-01");
        assertThat(reading.temperatureC()).isEqualByComparingTo("18.5");
        assertThat(reading.specificGravity()).isEqualByComparingTo("1.048");
    }

    @Test
    @DisplayName("uma mensagem com duas grandezas vira duas leituras")
    void umaMensagemDuasLeituras() {
        // Um iSpindel mede densidade e temperatura ao mesmo tempo; do nosso lado são dois fatos sobre
        // grandezas diferentes.
        var reading = PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "T-1", "externalReadingId", "msg-1",
                "measuredAt", "2026-08-09T10:00:00Z",
                "temperatureC", 18.5, "specificGravity", 1.048));

        var measures = reading.measures();

        assertThat(measures).containsOnlyKeys(Measure.TEMPERATURE, Measure.DENSITY);
        assertThat(measures.get(Measure.TEMPERATURE).unit()).isEqualTo("C");
        assertThat(measures.get(Measure.DENSITY).unit()).isEqualTo("SG");
    }

    @Test
    @DisplayName("as chaves das leituras DERIVAM do identificador da mensagem")
    void chavesDerivamDaMensagem() {
        // É o que faz a idempotência de INT-001 valer aqui: o aparelho que reenvia a mesma mensagem produz
        // as mesmas chaves, e as leituras já gravadas são reconhecidas como repetição. Sortear a chave
        // faria do adapter o furo por onde a idempotência vaza.
        var payload = Map.<String, Object>of("deviceId", "T-1", "externalReadingId", "msg-1",
                "measuredAt", "2026-08-09T10:00:00Z", "temperatureC", 18.5, "specificGravity", 1.048);

        var primeira = PayloadFormat.CANONICAL.translate(payload);
        var segunda = PayloadFormat.CANONICAL.translate(payload);

        assertThat(primeira.messageIdFor(Measure.TEMPERATURE))
                .isEqualTo(segunda.messageIdFor(Measure.TEMPERATURE))
                .isEqualTo("msg-1:TEMPERATURE");
        // E as duas grandezas do mesmo envio não disputam a mesma chave.
        assertThat(primeira.messageIdFor(Measure.TEMPERATURE))
                .isNotEqualTo(primeira.messageIdFor(Measure.DENSITY));
    }

    @Test
    @DisplayName("pressão em kPa vira bar na borda, sem arredondamento que invente precisão")
    void pressaoConvertida() {
        var reading = PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "T-1", "externalReadingId", "m-1",
                "measuredAt", "2026-08-09T10:00:00Z", "pressureKpa", 150));

        var pressao = reading.measures().get(Measure.PRESSURE);

        assertThat(pressao.amount()).isEqualByComparingTo("1.5");
        assertThat(pressao.unit()).isEqualTo("BAR");
    }

    @Test
    @DisplayName("iSpindel: traduz name/gravity/temperature e carimba o instante da chegada")
    void ispindel() {
        // O aparelho não tem relógio confiável, então o instante é o da chegada. A consequência é
        // declarada: para um iSpindel o atraso medido por INT-001 é sempre próximo de zero.
        var reading = PayloadFormat.ISPINDEL.translate(Map.of(
                "name", "iSpindel01", "ID", "4242", "temperature", 18.5, "gravity", 1.048));

        assertThat(reading.deviceId()).isEqualTo("iSpindel01");
        assertThat(reading.temperatureC()).isEqualByComparingTo("18.5");
        assertThat(reading.specificGravity()).isEqualByComparingTo("1.048");
        assertThat(reading.externalReadingId()).startsWith("4242@");
    }

    @Test
    @DisplayName("Tilt: converte Fahrenheit para Celsius na borda")
    void tilt() {
        // Guardar Fahrenheit e converter na leitura espalharia a conversão por toda consulta da série.
        var reading = PayloadFormat.TILT.translate(Map.of("Color", "RED", "Temp", 65, "SG", 1.048));

        assertThat(reading.deviceId()).isEqualTo("RED");
        assertThat(reading.temperatureC()).isEqualByComparingTo("18.33");
    }

    @Test
    @DisplayName("número como texto é aceito: muitos firmwares serializam tudo como string")
    void numeroComoTexto() {
        var reading = PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "T-1", "externalReadingId", "m-1",
                "measuredAt", "2026-08-09T10:00:00Z", "temperatureC", "18.5"));

        assertThat(reading.temperatureC()).isEqualByComparingTo("18.5");
    }

    @Test
    @DisplayName("texto que não é número é recusado dizendo QUAL campo está errado")
    void textoNaoNumericoERecusado() {
        // "payload inválido" mandaria quem configura o gateway adivinhar.
        assertThatThrownBy(() -> PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "T-1", "externalReadingId", "m-1",
                "measuredAt", "2026-08-09T10:00:00Z", "temperatureC", "quente")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperatureC");
    }

    @Test
    @DisplayName("campo obrigatório ausente é recusado pelo nome")
    void campoAusente() {
        assertThatThrownBy(() -> PayloadFormat.CANONICAL.translate(Map.of("externalReadingId", "m-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceId");
    }

    @Test
    @DisplayName("instante fora do ISO-8601 é recusado pelo nome do campo")
    void instanteInvalido() {
        assertThatThrownBy(() -> PayloadFormat.CANONICAL.translate(Map.of(
                "deviceId", "T-1", "externalReadingId", "m-1", "measuredAt", "ontem")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("measuredAt");
    }

    @Test
    @DisplayName("mensagem só com bateria e sinal é recusada: telemetria não é medição")
    void semGrandezaERecusada() {
        // Aceitá-la criaria linhas sem grandeza nenhuma na série do processo.
        var payload = new HashMap<String, Object>();
        payload.put("deviceId", "T-1");
        payload.put("externalReadingId", "m-1");
        payload.put("measuredAt", "2026-08-09T10:00:00Z");
        payload.put("batteryPercent", 92);

        assertThatThrownBy(() -> PayloadFormat.CANONICAL.translate(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grandeza");
    }

    @Test
    @DisplayName("formato desconhecido é recusado com o nome recebido")
    void formatoDesconhecido() {
        assertThatThrownBy(() -> PayloadFormat.of("inventado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inventado");
    }
}
