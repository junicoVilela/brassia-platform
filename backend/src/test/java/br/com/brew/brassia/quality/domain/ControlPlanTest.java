package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControlPlanTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-03T12:00:00Z");

    private static ControlPoint ponto(String parametro, Severity severidade, boolean critico) {
        return ControlPoint.of(parametro,
                new SpecLimits(new BigDecimal("4.5"), new BigDecimal("5.5"), null, "pH"),
                Frequency.perBatch(), "Ajustar com ácido lático e remeasurar", severidade, critico);
    }

    private static ControlPlan plano() {
        return ControlPlan.draft(BREWERY, "PC-001", "Controle de mosto", null, ProcessStage.BREWING);
    }

    private static ControlPlan publicado() {
        var p = plano();
        p.addPoint(ponto("pH do mosto", Severity.MAJOR, false));
        p.publish();
        return p;
    }

    @Test
    void nasceRascunhoNaVersaoUm() {
        var p = plano();

        assertThat(p.status()).isEqualTo(ControlPlanStatus.DRAFT);
        assertThat(p.version()).isEqualTo(1);
        assertThat(p.points()).isEmpty();
        assertThat(p.recipeId()).isEmpty();
    }

    @Test
    void planoSemPontoNaoPublica() {
        assertThatThrownBy(() -> plano().publish())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem ponto de controle");
    }

    @Test
    void publicadoEhImutavel() {
        var p = publicado();

        assertThatThrownBy(() -> p.addPoint(ponto("Densidade", Severity.MINOR, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crie uma nova versão");
        assertThatThrownBy(() -> p.amend("Outro nome", null, ProcessStage.PACKAGING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(p::publish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void novaVersaoNasceRascunhoPreservandoAAnterior() {
        var publicado = publicado();

        var nova = publicado.newDraftVersion();

        assertThat(nova.version()).isEqualTo(2);
        assertThat(nova.status()).isEqualTo(ControlPlanStatus.DRAFT);
        assertThat(nova.points()).hasSize(1);
        // A publicada continua publicada e julgando.
        assertThat(publicado.status()).isEqualTo(ControlPlanStatus.PUBLISHED);
    }

    @Test
    void aNovaVersaoCopiaOsPontosComIdentidadeNova() {
        // Reaproveitar os ids faria duas versões apontarem para o mesmo ponto, e uma medição antiga
        // passaria a referenciar o limite da versão nova — o oposto do que o versionamento serve.
        var publicado = publicado();
        var idAntigo = publicado.points().get(0).id();

        var nova = publicado.newDraftVersion();

        assertThat(nova.points().get(0).id()).isNotEqualTo(idAntigo);
        assertThat(nova.points().get(0).parameter()).isEqualTo(publicado.points().get(0).parameter());
        assertThat(nova.points().get(0).limits()).isEqualTo(publicado.points().get(0).limits());
    }

    @Test
    void soPlanoPublicadoGeraNovaVersao() {
        assertThatThrownBy(() -> plano().newDraftVersion()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rascunhoNaoJulgaMedicao() {
        // Rascunho pode ter limite pela metade; o veredito mudaria sozinho ao salvar a edição.
        var p = plano();
        p.addPoint(ponto("pH do mosto", Severity.MAJOR, false));
        var pointId = p.points().get(0).id();

        assertThatThrownBy(() -> p.judge(pointId, new BigDecimal("5.0")))
                .isInstanceOf(PlanNotPublishedException.class)
                .satisfies(e -> assertThat(((PlanNotPublishedException) e).planCode()).isEqualTo("PC-001"));
    }

    @Test
    void julgaDentroEForaDaFaixa() {
        var p = publicado();
        var pointId = p.points().get(0).id();

        assertThat(p.judge(pointId, new BigDecimal("5.0"))).isEmpty();
        assertThat(p.judge(pointId, new BigDecimal("6.0"))).isPresent();
    }

    @Test
    void naoControlaOMesmoParametroDuasVezes() {
        var p = plano();
        p.addPoint(ponto("pH do mosto", Severity.MAJOR, false));

        assertThatThrownBy(() -> p.addPoint(ponto("PH DO MOSTO", Severity.MINOR, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já controla o parâmetro");
    }

    @Test
    void removePontoDoRascunho() {
        var p = plano();
        p.addPoint(ponto("pH do mosto", Severity.MAJOR, false));
        var pointId = p.points().get(0).id();

        p.removePoint(pointId);

        assertThat(p.points()).isEmpty();
        assertThatThrownBy(() -> p.removePoint(pointId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pontoExigeAcaoPrescrita() {
        // Ponto sem ação não é controle, é observação: ninguém sabe o que fazer com o desvio.
        assertThatThrownBy(() -> ControlPoint.of("pH",
                new SpecLimits(new BigDecimal("4"), new BigDecimal("5"), null, "pH"),
                Frequency.perBatch(), "  ", Severity.MAJOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ação é obrigatório");
    }

    // --- desvio ---

    @Test
    void oDesvioHerdaASeveridadeDoPontoENaoDaMedicao() {
        var plano = plano();
        plano.addPoint(ponto("pH do mosto", Severity.CRITICAL, false));
        plano.publish();
        var point = plano.points().get(0);
        var violacao = plano.judge(point.id(), new BigDecimal("6.2")).orElseThrow();

        var desvio = Deviation.open(BREWERY, UUID.randomUUID(), plano, point, violacao,
                new BigDecimal("6.2"), AGORA, ATOR);

        assertThat(desvio.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(desvio.bound()).isEqualTo(SpecLimits.Bound.ABOVE_MAX);
        assertThat(desvio.limitValue()).isEqualByComparingTo("5.5");
        assertThat(desvio.action()).isEqualTo("Ajustar com ácido lático e remeasurar");
        assertThat(desvio.status()).isEqualTo(DeviationStatus.OPEN);
    }

    @Test
    void oDesvioDizOQuantoPassouDoLimite() {
        var plano = publicado();
        var point = plano.points().get(0);

        var acima = Deviation.open(BREWERY, UUID.randomUUID(), plano, point,
                plano.judge(point.id(), new BigDecimal("6.0")).orElseThrow(), new BigDecimal("6.0"), AGORA,
                ATOR);
        var abaixo = Deviation.open(BREWERY, UUID.randomUUID(), plano, point,
                plano.judge(point.id(), new BigDecimal("4.0")).orElseThrow(), new BigDecimal("4.0"), AGORA,
                ATOR);

        assertThat(acima.excess()).isEqualByComparingTo("0.5");
        assertThat(abaixo.excess()).isEqualByComparingTo("0.5");
        assertThat(acima.describe()).contains("acima d", "5.5");
    }

    @Test
    void severidadeLeveNaoAlertaOLote() {
        assertThat(Severity.MINOR.alertsBatch()).isFalse();
        assertThat(Severity.MAJOR.alertsBatch()).isTrue();
        assertThat(Severity.CRITICAL.alertsBatch()).isTrue();
    }
}
