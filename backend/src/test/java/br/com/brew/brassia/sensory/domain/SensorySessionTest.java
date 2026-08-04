package br.com.brew.brassia.sensory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SensorySessionTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID LOTE_A = UUID.randomUUID();
    private static final UUID LOTE_B = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-03T14:00:00Z");
    private static final LocalDate DATA = LocalDate.parse("2026-08-03");

    private static SensorySession sessao() {
        return SensorySession.draft(BREWERY, "SEN-001", "Comparativo de lote", DATA);
    }

    private static Map<SensoryAttribute, Integer> notas(int valor) {
        var m = new EnumMap<SensoryAttribute, Integer>(SensoryAttribute.class);
        for (var a : SensoryAttribute.values()) {
            m.put(a, valor);
        }
        return m;
    }

    private static SensoryEvaluation ficha(SensorySession s, SensorySample amostra, int nota,
            List<String> descritores) {
        return SensoryEvaluation.submit(BREWERY, s.id(), amostra.id(), UUID.randomUUID(), notas(nota),
                descritores, null, AGORA);
    }

    // --- código cego ---

    @Test
    void oCodigoCegoTemTresDigitosENaoEhSequencial() {
        var s = sessao();

        var codigos = IntStream.range(0, 20)
                .mapToObj(i -> s.addSample(LOTE_A, null).blindCode().value())
                .toList();

        assertThat(codigos).allMatch(c -> c.matches("\\d{3}"));
        // Sequencial vazaria a ordem de preparo, e ordem é informação.
        assertThat(codigos).isNotEqualTo(codigos.stream().sorted().toList());
    }

    @Test
    void osCodigosCegosNaoSeRepetemNaSessao() {
        var s = sessao();

        var codigos = IntStream.range(0, 50)
                .mapToObj(i -> s.addSample(LOTE_A, null).blindCode().value())
                .toList();

        assertThat(Set.copyOf(codigos)).hasSize(codigos.size());
    }

    @Test
    void recusaCodigoCegoInvalido() {
        assertThatThrownBy(() -> new BlindCode("12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlindCode("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlindCode("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlindCode("000")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- ciclo da sessão ---

    @Test
    void nasceRascunhoSemAmostra() {
        var s = sessao();

        assertThat(s.status()).isEqualTo(SessionStatus.DRAFT);
        assertThat(s.samples()).isEmpty();
    }

    @Test
    void sessaoSemAmostraNaoAbre() {
        assertThatThrownBy(() -> sessao().open(AGORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem amostra");
    }

    @Test
    void abertaNaoMudaDeAmostras() {
        var s = sessao();
        s.addSample(LOTE_A, null);
        s.open(AGORA);

        assertThatThrownBy(() -> s.addSample(LOTE_B, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.amend("outro", DATA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soSessaoAbertaRecebeFicha() {
        var s = sessao();
        s.addSample(LOTE_A, null);

        assertThatThrownBy(s::requireAcceptingEvaluations)
                .isInstanceOf(SessionNotOpenException.class)
                .satisfies(e -> assertThat(((SessionNotOpenException) e).status()).isEqualTo("Rascunho"));

        s.open(AGORA);
        s.requireAcceptingEvaluations();

        s.close(AGORA);
        assertThatThrownBy(s::requireAcceptingEvaluations).isInstanceOf(SessionNotOpenException.class);
    }

    @Test
    void soSessaoEmAvaliacaoEhEncerrada() {
        var s = sessao();
        s.addSample(LOTE_A, null);

        assertThatThrownBy(() -> s.close(AGORA)).isInstanceOf(IllegalStateException.class);
    }

    // --- o resultado não aparece antes do fechamento ---

    @Test
    void oResultadoNaoApareceComASessaoAberta() {
        // O critério da história: ver a nota alheia antes de dar a sua faz o painel convergir.
        var s = sessao();
        var amostra = s.addSample(LOTE_A, null);
        var fichas = List.of(ficha(s, amostra, 8, List.of()));

        assertThatThrownBy(() -> s.results(fichas))
                .isInstanceOf(ResultsNotAvailableException.class)
                .satisfies(e -> assertThat(((ResultsNotAvailableException) e).status()).isEqualTo("Rascunho"));

        s.open(AGORA);
        assertThatThrownBy(() -> s.results(fichas))
                .isInstanceOf(ResultsNotAvailableException.class)
                .satisfies(e -> assertThat(((ResultsNotAvailableException) e).status())
                        .isEqualTo("Em avaliação"));
    }

    @Test
    void oFechamentoRevelaMediaLoteEDescritores() {
        var s = sessao();
        var amostra = s.addSample(LOTE_A, "servida a 8 °C");
        s.open(AGORA);
        s.close(AGORA);

        var resultado = s.results(List.of(
                ficha(s, amostra, 8, List.of("cítrico", "resinoso")),
                ficha(s, amostra, 6, List.of("cítrico"))));

        var linha = resultado.samples().get(0);
        assertThat(linha.blindCode()).isEqualTo(amostra.blindCode().value());
        // O lote só aparece agora — e nunca foi apagado.
        assertThat(linha.batchId()).isEqualTo(LOTE_A);
        assertThat(linha.evaluations()).isEqualTo(2);
        assertThat(linha.overallAverage()).isEqualByComparingTo("7.00");
        assertThat(linha.spread()).isEqualByComparingTo("2");
        assertThat(linha.descriptors()).containsExactly("cítrico", "resinoso");
    }

    @Test
    void amostraSemFichaNaoQuebraOResultado() {
        var s = sessao();
        s.addSample(LOTE_A, null);
        s.open(AGORA);
        s.close(AGORA);

        var resultado = s.results(List.of());

        assertThat(resultado.samples()).hasSize(1);
        assertThat(resultado.samples().get(0).evaluations()).isZero();
        assertThat(resultado.samples().get(0).overallAverage()).isEqualByComparingTo("0");
    }

    // --- viés do painel ---

    @Test
    void oMesmoLoteEntraDuasVezesComCodigosDiferentes() {
        // Duplicata cega é técnica, não engano: mede a consistência de quem prova.
        var s = sessao();
        var primeira = s.addSample(LOTE_A, null);
        var segunda = s.addSample(LOTE_A, null);

        assertThat(primeira.batchId()).isEqualTo(segunda.batchId());
        assertThat(primeira.blindCode()).isNotEqualTo(segunda.blindCode());
    }

    @Test
    void aComparacaoAcusaViesQuandoOMesmoLoteRecebeNotasDistintas() {
        var s = sessao();
        var primeira = s.addSample(LOTE_A, null);
        var segunda = s.addSample(LOTE_A, null);
        s.open(AGORA);
        s.close(AGORA);

        // A cerveja era a mesma; a diferença de 5 pontos é do painel.
        var resultado = s.results(List.of(ficha(s, primeira, 9, List.of()), ficha(s, segunda, 4, List.of())));

        assertThat(resultado.consistency()).hasSize(1);
        var comparacao = resultado.consistency().get(0);
        assertThat(comparacao.batchId()).isEqualTo(LOTE_A);
        assertThat(comparacao.difference()).isEqualByComparingTo("5.00");
        assertThat(comparacao.blindCodes()).hasSize(2);
    }

    @Test
    void semDuplicataNaoHaComparacaoDeConsistencia() {
        var s = sessao();
        var a = s.addSample(LOTE_A, null);
        var b = s.addSample(LOTE_B, null);
        s.open(AGORA);
        s.close(AGORA);

        var resultado = s.results(List.of(ficha(s, a, 8, List.of()), ficha(s, b, 5, List.of())));

        assertThat(resultado.consistency()).isEmpty();
    }

    // --- ficha ---

    @Test
    void aFichaExigeTodosOsAtributos() {
        var s = sessao();
        var amostra = s.addSample(LOTE_A, null);
        var incompleta = new EnumMap<SensoryAttribute, Integer>(SensoryAttribute.class);
        incompleta.put(SensoryAttribute.AROMA, 7);

        assertThatThrownBy(() -> SensoryEvaluation.submit(BREWERY, s.id(), amostra.id(), UUID.randomUUID(),
                incompleta, List.of(), null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("falta a nota de");
    }

    @Test
    void aNotaFicaEntreZeroEDez() {
        var s = sessao();
        var amostra = s.addSample(LOTE_A, null);

        assertThatThrownBy(() -> ficha(s, amostra, 11, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 0 e 10");
        assertThatThrownBy(() -> ficha(s, amostra, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        // Os extremos são válidos.
        assertThat(ficha(s, amostra, 0, List.of()).score(SensoryAttribute.OVERALL)).isZero();
        assertThat(ficha(s, amostra, 10, List.of()).score(SensoryAttribute.AROMA)).isEqualTo(10);
    }

    @Test
    void aAmostraGuardaOLoteEOCodigoCego() {
        var s = sessao();
        var amostra = s.addSample(LOTE_A, "servida a 8 °C");

        assertThat(amostra.batchId()).isEqualTo(LOTE_A);
        assertThat(amostra.note()).isEqualTo("servida a 8 °C");
        assertThat(s.sample(amostra.id())).isPresent();
    }

    @Test
    void removeAmostraDoRascunho() {
        var s = sessao();
        var amostra = s.addSample(LOTE_A, null);

        s.removeSample(amostra.id());

        assertThat(s.samples()).isEmpty();
        assertThatThrownBy(() -> s.removeSample(amostra.id())).isInstanceOf(IllegalArgumentException.class);
    }
}
