package br.com.brew.brassia.sensor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A leitura recebida (INT-001).
 *
 * <p>O que estes testes fixam é o critério da história: <strong>qualidade e atraso são sinalizados</strong>.
 * Nenhum deles recusa a leitura, porque recusar não deixa "nada" — deixa um buraco na curva, e um buraco é
 * indistinguível de "não aconteceu nada".
 */
class SensorReadingTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant MEDIU = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant CHEGOU = Instant.parse("2026-08-09T10:00:30Z");

    private static SensorDevice termometro() {
        return SensorDevice.register(CERVEJARIA, "TANK-01-TEMP", "Termômetro do tanque 1",
                Measure.TEMPERATURE, "C", null, Duration.ofMinutes(5), OPERADOR, MEDIU);
    }

    @Test
    @DisplayName("leitura plausível e pontual é GOOD, sem motivo")
    void leituraBoa() {
        var reading = SensorReading.receive(termometro(), "msg-1", new BigDecimal("18.5"), "C",
                MEDIU, CHEGOU);

        assertThat(reading.quality()).isEqualTo(ReadingQuality.GOOD);
        assertThat(reading.qualityReason()).isNull();
        assertThat(reading.lateness().late()).isFalse();
        assertThat(reading.lateness().delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(reading.unit()).isEqualTo("C");
    }

    @Test
    @DisplayName("valor fora da faixa é GRAVADO e sinalizado, não recusado")
    void foraDaFaixaESinalizada() {
        // O ponto central da história. Um sensor fora d'água reporta 85 °C num fermentador; recusar deixaria
        // a curva com um vazio que parece "não mediu". Gravado e marcado, conta as duas coisas verdadeiras:
        // o dispositivo reportou, e não se deve acreditar no número.
        var reading = SensorReading.receive(termometro(), "msg-2", new BigDecimal("85"), "C",
                MEDIU, CHEGOU);

        assertThat(reading.quality()).isEqualTo(ReadingQuality.OUT_OF_RANGE);
        assertThat(reading.qualityReason()).contains("85").contains("fora da faixa");
        assertThat(reading.value()).isEqualByComparingTo("85");
    }

    @Test
    @DisplayName("medida no futuro é FUTURE_CLOCK e tem precedência sobre a faixa")
    void futuroTemPrecedenciaSobreFaixa() {
        // Um dispositivo que volta de reset com o relógio de fábrica manda valores perfeitos com instante
        // impossível. Dizer "fora da faixa" responderia a pergunta errada: o problema não é o número, é que
        // a leitura não pode ser posicionada na série.
        var reading = SensorReading.receive(termometro(), "msg-3", new BigDecimal("900"), "C",
                CHEGOU, MEDIU);

        assertThat(reading.quality()).isEqualTo(ReadingQuality.FUTURE_CLOCK);
        assertThat(reading.qualityReason()).contains("futuro");
        assertThat(reading.lateness().delay().isNegative()).isTrue();
    }

    @Test
    @DisplayName("leitura atrasada além do intervalo é sinalizada sem perder a qualidade do valor")
    void atrasoEQualidadeSaoEixosIndependentes() {
        // Gateway ficou sem rede por 20 min: o valor está perfeito e a chegada está atrasada. Um "status"
        // único obrigaria a escolher qual das duas informações perder.
        var reading = SensorReading.receive(termometro(), "msg-4", new BigDecimal("18.5"), "C",
                MEDIU, MEDIU.plus(Duration.ofMinutes(20)));

        assertThat(reading.quality()).isEqualTo(ReadingQuality.GOOD);
        assertThat(reading.lateness().late()).isTrue();
        assertThat(reading.lateness().delay()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    @DisplayName("os dois relógios ficam gravados")
    void guardaOsDoisRelogios() {
        // Só o nosso: leituras represadas por queda de rede viram uma rajada simultânea que nunca existiu.
        // Só o dele: relógio errado reescreve a história sem sobrar nada com que comparar.
        var reading = SensorReading.receive(termometro(), "msg-5", new BigDecimal("18.5"), "C",
                MEDIU, CHEGOU);

        assertThat(reading.measuredAt()).isEqualTo(MEDIU);
        assertThat(reading.receivedAt()).isEqualTo(CHEGOU);
    }

    @Test
    @DisplayName("identificador da mensagem é obrigatório: sem ele não há repetição reconhecível")
    void exigeIdentificadorDaMensagem() {
        assertThatThrownBy(() -> SensorReading.receive(termometro(), null, new BigDecimal("18.5"), "C",
                MEDIU, CHEGOU))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SensorReading.receive(termometro(), "   ", new BigDecimal("18.5"), "C",
                MEDIU, CHEGOU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("identificador longo demais é recusado")
    void recusaIdentificadorLongo() {
        assertThatThrownBy(() -> SensorReading.receive(termometro(), "x".repeat(81),
                new BigDecimal("18.5"), "C", MEDIU, CHEGOU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duas leituras idênticas produzem ids diferentes: a chave é a mensagem, não o conteúdo")
    void chaveEDaMensagemNaoDoConteudo() {
        // Um hash do payload trataria duas medições legitimamente idênticas — sensor parado, mesmo segundo,
        // mesmo valor — como a mesma mensagem, e descartaria uma leitura verdadeira.
        var device = termometro();
        var primeira = SensorReading.receive(device, "msg-a", new BigDecimal("18.5"), "C", MEDIU, CHEGOU);
        var segunda = SensorReading.receive(device, "msg-b", new BigDecimal("18.5"), "C", MEDIU, CHEGOU);

        assertThat(primeira.id()).isNotEqualTo(segunda.id());
        assertThat(primeira.messageId()).isNotEqualTo(segunda.messageId());
    }
}
