package br.com.brew.brassia.sensor.adapter.outbound.fermentation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.fermentation.FermentationCommands;
import br.com.brew.brassia.production.VesselOccupancyLookup;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.ReadingQuality;
import br.com.brew.brassia.sensor.domain.SensorDevice;
import br.com.brew.brassia.sensor.domain.SensorReading;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tradução entre o vocabulário do dispositivo e o do lote (DEB-INT-001).
 *
 * <p>É aqui que moram as decisões do encaminhamento, e nenhuma é óbvia: qual grandeza atravessa, qual
 * qualidade atravessa, e o que acontece quando o tanque está vazio. Testar isso pelo caso de uso de
 * ingestão misturaria essas regras com a idempotência, que é outra história.
 */
class FermentationCurveFeedAdapterTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID FERMENTADOR = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final Instant MEDIU = Instant.parse("2026-08-09T10:00:00Z");
    private static final Instant RECEBEU = MEDIU.plus(Duration.ofSeconds(30));

    private final FakeFermentation fermentation = new FakeFermentation();
    private final FakeVessels vessels = new FakeVessels();
    private final FermentationCurveFeedAdapter feed =
            new FermentationCurveFeedAdapter(vessels, fermentation);

    @Test
    @DisplayName("temperatura de um tanque ocupado vira ponto na curva do lote")
    void temperaturaViraPonto() {
        feed.forward(leitura(Measure.TEMPERATURE, "C", "18.5"), FERMENTADOR);

        assertThat(fermentation.recebidas).hasSize(1);
        var recebida = fermentation.recebidas.get(0);
        assertThat(recebida.batchId()).isEqualTo(LOTE);
        assertThat(recebida.kind()).isEqualTo("TEMPERATURE");
        assertThat(recebida.value()).isEqualByComparingTo("18.5");
        assertThat(recebida.measuredAt()).isEqualTo(MEDIU);
    }

    @Test
    @DisplayName("VAZÃO não vira ponto: um lote não tem vazão")
    void vazaoNaoAtravessa() {
        // FLOW é grandeza de tubulação. O dispositivo continua gravando a série dele — ele só não tem o
        // que dizer sobre a curva de um lote. É a razão de as duas enums existirem separadas.
        feed.forward(leitura(Measure.FLOW, "L_MIN", "12"), FERMENTADOR);

        assertThat(fermentation.recebidas).isEmpty();
    }

    @Test
    @DisplayName("valor FORA DA FAIXA atravessa, e é divergência deliberada do critério do débito")
    void foraDaFaixaAtravessa() {
        // O critério escrito dizia "encaminha a leitura GOOD". Filtrar aqui esconderia da curva exatamente
        // o sintoma de sensor sujo ou fora d'água — e um buraco na curva é indistinguível de "não mediu".
        // A fermentação avalia a plausibilidade por conta própria e grava o ponto sinalizado.
        var absurda = leitura(Measure.TEMPERATURE, "C", "800");
        assertThat(absurda.quality()).isEqualTo(ReadingQuality.OUT_OF_RANGE);

        feed.forward(absurda, FERMENTADOR);

        assertThat(fermentation.recebidas).hasSize(1);
    }

    @Test
    @DisplayName("RELÓGIO NO FUTURO não atravessa: o instante é a chave da curva")
    void relogioNoFuturoNaoAtravessa() {
        // Assimetria proposital com o caso acima. Um valor absurdo fica visivelmente errado no gráfico; um
        // instante inventado não — ele ordena a curva e mente sobre a sequência dos fatos, que é a única
        // coisa que uma curva de fermentação serve para contar.
        var doFuturo = SensorReading.receive(dispositivo(Measure.TEMPERATURE, "C"), "msg-futuro",
                new BigDecimal("18.5"), "C", RECEBEU.plus(Duration.ofHours(3)), RECEBEU);
        assertThat(doFuturo.quality()).isEqualTo(ReadingQuality.FUTURE_CLOCK);

        feed.forward(doFuturo, FERMENTADOR);

        assertThat(fermentation.recebidas).isEmpty();
    }

    @Test
    @DisplayName("tanque vazio não é erro — é o estado entre dois lotes")
    void tanqueVazioNaoEErro() {
        vessels.ocupado = false;

        feed.forward(leitura(Measure.TEMPERATURE, "C", "18.5"), FERMENTADOR);

        assertThat(fermentation.recebidas).isEmpty();
    }

    @Test
    @DisplayName("dispositivo sem equipamento vinculado não procura lote nenhum")
    void semEquipamentoNaoProcura() {
        feed.forward(leitura(Measure.TEMPERATURE, "C", "18.5"), null);

        assertThat(vessels.consultas).isZero();
        assertThat(fermentation.recebidas).isEmpty();
    }

    private static SensorDevice dispositivo(Measure measure, String unit) {
        return SensorDevice.register(CERVEJARIA, "TANK-01", "Sonda", measure, unit, FERMENTADOR,
                Duration.ofMinutes(5), OPERADOR, MEDIU);
    }

    private static SensorReading leitura(Measure measure, String unit, String value) {
        return SensorReading.receive(dispositivo(measure, unit), "msg-" + value, new BigDecimal(value),
                unit, MEDIU, RECEBEU);
    }

    /** Dublê da porta publicada da fermentação: guarda o que recebeu, sem gravar nada. */
    private static final class FakeFermentation implements FermentationCommands {

        private final List<FermentationCommands.SensorReading> recebidas = new ArrayList<>();

        @Override
        public Recorded recordSensorReading(FermentationCommands.SensorReading reading) {
            recebidas.add(reading);
            return new Recorded(UUID.randomUUID(), true, true);
        }
    }

    /** Dublê da ocupação do fermentador; conta consultas para provar que sem equipamento não se pergunta. */
    private static final class FakeVessels implements VesselOccupancyLookup {

        private boolean ocupado = true;
        private int consultas;

        @Override
        public Optional<UUID> fermentingBatchOf(UUID breweryId, UUID equipmentId) {
            consultas++;
            return ocupado ? Optional.of(LOTE) : Optional.empty();
        }
    }
}
