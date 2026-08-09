package br.com.brew.brassia.digitaltwin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.digitaltwin.application.port.inbound.ControlChartQueries;
import br.com.brew.brassia.digitaltwin.application.service.ControlChartService;
import br.com.brew.brassia.digitaltwin.domain.ControlLimits;
import br.com.brew.brassia.digitaltwin.domain.ControlSignal;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchMeasurementLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A montagem da carta de controle (SPC-001).
 *
 * <p>O que estes testes fixam: a série é do <strong>processo</strong>, não de um lote — ela atravessa lotes
 * na ordem em que as medições aconteceram, porque é entre dois lotes que o processo costuma mudar.
 */
class ControlChartServiceTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID OUTRA = UUID.randomUUID();
    private static final UUID RECEITA = UUID.randomUUID();
    private static final UUID OUTRA_RECEITA = UUID.randomUUID();
    private static final Instant BASE = Instant.parse("2026-08-01T10:00:00Z");

    private FakeBatches batches;
    private FakeMeasurements measurements;
    private ControlChartService service;

    @BeforeEach
    void setUp() {
        batches = new FakeBatches();
        measurements = new FakeMeasurements();
        service = new ControlChartService(batches, measurements);
    }

    /** Cria um lote com N medições estáveis, começando em `offsetMinutos`. */
    private UUID loteComSerie(int quantas, int offsetMinutos) {
        return loteComSerie(CERVEJARIA, RECEITA, quantas, offsetMinutos, "C");
    }

    private UUID loteComSerie(UUID brewery, UUID recipe, int quantas, int offsetMinutos, String unit) {
        var id = UUID.randomUUID();
        batches.add(brewery, id, recipe);
        for (int i = 0; i < quantas; i++) {
            measurements.add(brewery, id, "TEMPERATURE",
                    new BigDecimal(i % 2 == 0 ? "19" : "21"), unit,
                    BASE.plusSeconds((offsetMinutos + i) * 60L));
        }
        return id;
    }

    private ControlChartQueries.Request pedido(List<UUID> lotes) {
        return new ControlChartQueries.Request(CERVEJARIA, RECEITA, "TEMPERATURE", lotes);
    }

    @Test
    @DisplayName("a série ATRAVESSA os lotes: a carta é do processo, não de um lote")
    void serieAtravessaLotes() {
        // O momento em que o processo muda cai entre dois lotes tanto quanto dentro de um.
        var primeiro = loteComSerie(10, 0);
        var segundo = loteComSerie(10, 100);

        var chart = service.analyze(pedido(List.of(primeiro, segundo)));

        assertThat(chart.points()).hasSize(20);
        assertThat(chart.controlLimits().sampleSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("os pontos saem em ordem CRONOLÓGICA, mesmo se os lotes vierem fora de ordem")
    void ordenaPorInstante() {
        // Uma lista ordenada por outra coisa produziria sinais que o processo nunca deu.
        var antigo = loteComSerie(10, 0);
        var recente = loteComSerie(10, 100);

        var chart = service.analyze(pedido(List.of(recente, antigo)));

        var instantes = chart.points().stream().map(ControlChartQueries.Point::measuredAt).toList();
        assertThat(instantes).isSorted();
    }

    @Test
    @DisplayName("processo estável: nenhum sinal, e a carta diz que está sob controle")
    void estavelSobControle() {
        var chart = service.analyze(pedido(List.of(loteComSerie(20, 0))));

        assertThat(chart.signals()).isEmpty();
        assertThat(chart.inControl()).isTrue();
    }

    @Test
    @DisplayName("um ponto fora dos limites tira a carta do controle")
    void pontoForaTiraDoControle() {
        var lote = loteComSerie(20, 0);
        measurements.add(CERVEJARIA, lote, "TEMPERATURE", new BigDecimal("45"), "C",
                BASE.plusSeconds(30 * 60L));

        var chart = service.analyze(pedido(List.of(lote)));

        assertThat(chart.inControl()).isFalse();
        assertThat(chart.signals()).anyMatch(s -> s.kind() == ControlSignal.Kind.BEYOND_LIMIT);
    }

    @Test
    @DisplayName("histórico curto é recusado, não vira limite frouxo")
    void historicoCurtoRecusado() {
        assertThatThrownBy(() -> service.analyze(pedido(List.of(loteComSerie(5, 0)))))
                .isInstanceOf(ControlLimits.InsufficientHistoryException.class);
    }

    @Test
    @DisplayName("UNIDADES MISTURADAS são recusadas, não convertidas em silêncio")
    void unidadesMisturadasRecusadas() {
        // Misturar °C e °F produziria limites que não descrevem nada. A conversão pertence a quem
        // registrou a medição, não a quem lê a série.
        var emCelsius = loteComSerie(20, 0);
        var emFahrenheit = loteComSerie(CERVEJARIA, RECEITA, 5, 100, "F");

        assertThatThrownBy(() -> service.analyze(pedido(List.of(emCelsius, emFahrenheit))))
                .isInstanceOf(ControlChartService.MixedUnitsException.class);
    }

    @Test
    @DisplayName("lote de outra receita não entra na série")
    void outraReceitaNaoEntra() {
        var daReceita = loteComSerie(20, 0);
        var deOutra = loteComSerie(CERVEJARIA, OUTRA_RECEITA, 20, 100, "C");

        var chart = service.analyze(pedido(List.of(daReceita, deOutra)));

        assertThat(chart.points()).hasSize(20);
    }

    @Test
    @DisplayName("lote de outra cervejaria não resolve e não entra")
    void outraCervejariaNaoEntra() {
        var meu = loteComSerie(20, 0);
        var alheio = loteComSerie(OUTRA, RECEITA, 20, 100, "C");

        var chart = service.analyze(pedido(List.of(meu, alheio)));

        assertThat(chart.points()).hasSize(20);
    }

    @Test
    @DisplayName("os limites vêm CALCULADOS da série; não há campo para especificação")
    void limitesCalculadosSemCampoDeEspecificacao() {
        // A separação é estrutural: a carta não tem onde receber um limite escolhido.
        var chart = service.analyze(pedido(List.of(loteComSerie(20, 0))));

        assertThat(chart.controlLimits().centerLine()).isEqualByComparingTo("20");
        var campos = java.util.Arrays.stream(ControlChartQueries.Chart.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(campos).doesNotContain("specLimits", "spec", "specification");
    }

    @Test
    @DisplayName("lista vazia e grandeza em branco são recusadas")
    void entradasInvalidas() {
        assertThatThrownBy(() -> service.analyze(pedido(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.analyze(
                new ControlChartQueries.Request(CERVEJARIA, RECEITA, "  ", List.of(UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- dublês ---

    private static final class FakeBatches implements BatchLookup {
        private final Map<String, Snapshot> byKey = new HashMap<>();

        void add(UUID breweryId, UUID batchId, UUID recipeId) {
            byKey.put(breweryId + "|" + batchId, new Snapshot(batchId, UUID.randomUUID(), "LOTE",
                    new BigDecimal("100"), new BigDecimal("100"), "DONE", recipeId, 1, "Receita"));
        }

        @Override public Optional<Snapshot> find(UUID breweryId, UUID batchId) {
            return Optional.ofNullable(byKey.get(breweryId + "|" + batchId));
        }
    }

    private static final class FakeMeasurements implements BatchMeasurementLookup {
        private final Map<String, List<Reading>> byKey = new HashMap<>();

        void add(UUID breweryId, UUID batchId, String kind, BigDecimal value, String unit, Instant at) {
            byKey.computeIfAbsent(breweryId + "|" + batchId + "|" + kind, k -> new ArrayList<>())
                    .add(new Reading(value, unit, at));
        }

        @Override public List<Reading> ofBatch(UUID breweryId, UUID batchId, String kind) {
            return byKey.getOrDefault(breweryId + "|" + batchId + "|" + kind, List.of());
        }
    }
}
