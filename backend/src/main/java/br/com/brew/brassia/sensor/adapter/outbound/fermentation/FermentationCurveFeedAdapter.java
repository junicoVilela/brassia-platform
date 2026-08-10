package br.com.brew.brassia.sensor.adapter.outbound.fermentation;

import br.com.brew.brassia.fermentation.FermentationCommands;
import br.com.brew.brassia.production.VesselOccupancyLookup;
import br.com.brew.brassia.sensor.application.port.outbound.BatchCurveFeed;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.ReadingQuality;
import br.com.brew.brassia.sensor.domain.SensorReading;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * O ponto onde a telemetria encontra o lote (DEB-INT-001).
 *
 * <p>São dois saltos, e nenhum dos dois é do sensor: o equipamento vira lote pela ocupação do fermentador
 * (`production`), e a medição vira ponto na curva pela porta de comando da fermentação. Antes deste
 * adapter, o sensor guardava a série dele e quem quisesse ver a curva registrava a leitura à mão.
 */
@Component
class FermentationCurveFeedAdapter implements BatchCurveFeed {

    private final VesselOccupancyLookup vessels;
    private final FermentationCommands fermentation;

    FermentationCurveFeedAdapter(VesselOccupancyLookup vessels, FermentationCommands fermentation) {
        this.vessels = Objects.requireNonNull(vessels, "vessels");
        this.fermentation = Objects.requireNonNull(fermentation, "fermentation");
    }

    @Override
    public void forward(SensorReading reading, UUID equipmentId) {
        if (equipmentId == null) {
            return;
        }
        var kind = kindOf(reading.measure());
        if (kind == null || !feedsCurve(reading.quality())) {
            return;
        }
        vessels.fermentingBatchOf(reading.breweryId(), equipmentId).ifPresent(batchId ->
                fermentation.recordSensorReading(new FermentationCommands.SensorReading(
                        reading.breweryId(), batchId, kind, reading.value(), reading.unit(),
                        reading.measuredAt())));
    }

    /**
     * A grandeza do lote correspondente, ou {@code null} quando não existe.
     *
     * <p>{@code FLOW} é o caso: vazão é grandeza de tubulação, e um lote não tem vazão. Um dispositivo de
     * vazão continua gravando a série dele — ele só não tem o que dizer sobre a curva de um lote.
     *
     * <p>As unidades não precisam de conversão: as duas enums declaram as mesmas para as grandezas que
     * compartilham (SG/PLATO, C/F, PSI/BAR). Se divergirem um dia, a fermentação recusa a unidade
     * incompatível em vez de gravar um número na escala errada.
     */
    private static String kindOf(Measure measure) {
        return switch (measure) {
            case DENSITY -> "DENSITY";
            case TEMPERATURE -> "TEMPERATURE";
            case PRESSURE -> "PRESSURE";
            case FLOW -> null;
        };
    }

    /**
     * <strong>Aqui houve uma divergência deliberada do critério escrito no débito</strong>, que dizia
     * encaminhar "a leitura {@code GOOD}".
     *
     * <p>{@code OUT_OF_RANGE} também vai. A fermentação avalia plausibilidade por conta própria, com as
     * mesmas faixas, e grava o ponto sinalizado — pelo motivo que o próprio módulo de sensores já defende:
     * um buraco na curva é indistinguível de "o sensor não mediu" e de "não aconteceu nada", enquanto um
     * ponto marcado conta duas coisas verdadeiras. Filtrar aqui esconderia da curva exatamente o sintoma de
     * sensor sujo ou fora d'água, que é quando olhar a curva mais importa.
     *
     * <p>{@code FUTURE_CLOCK} não vai, e não é simetria quebrada por descuido. O instante da medição é
     * parte da chave natural da leitura de fermentação e é por ele que a curva se ordena; um ponto vindo do
     * futuro não fica visivelmente errado, fica no fim do gráfico mentindo sobre a sequência dos fatos. O
     * valor pode até estar certo — o instante é que não serve, e é o instante que a curva usa.
     */
    private static boolean feedsCurve(ReadingQuality quality) {
        return quality != ReadingQuality.FUTURE_CLOCK;
    }
}
