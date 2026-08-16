package br.com.brew.brassia.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RatingAndReportTest {

    private static final UUID PUBLICACAO = UUID.randomUUID();
    private static final UUID PESSOA = UUID.randomUUID();
    private static final UUID MODERADOR = UUID.randomUUID();
    private static final Instant ONTEM = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant HOJE = Instant.parse("2026-08-15T10:00:00Z");

    // --- avaliação ---

    @Test
    void semAvaliacaoNaoHaMediaENaoEZero() {
        // Zero é a pior nota possível: uma receita nova nasceria parecendo péssima.
        var s = RatingSummary.none();

        assertThat(s.hasVotes()).isFalse();
        assertThat(s.average()).isNull();
        assertThat(s.meaningful()).isFalse();
    }

    @Test
    void aMediaNuncaViajaSemAContagem() {
        // "5,0" de uma avaliação e "5,0" de duzentas são o mesmo número e significam coisas opostas.
        var uma = RatingSummary.of(new BigDecimal("5"), 1);
        var muitas = RatingSummary.of(new BigDecimal("1000"), 200);

        assertThat(uma.average()).isEqualByComparingTo("5.0");
        assertThat(uma.count()).isEqualTo(1);
        assertThat(uma.meaningful()).isFalse();

        assertThat(muitas.average()).isEqualByComparingTo("5.0");
        assertThat(muitas.meaningful()).isTrue();
    }

    @Test
    void aMediaEArredondadaAUmaCasa() {
        // Duas casas dariam a impressão de precisão que cinco opiniões não têm.
        assertThat(RatingSummary.of(new BigDecimal("17"), 4).average()).isEqualByComparingTo("4.3");
    }

    @Test
    void aNotaVaiDeUmACinco() {
        // Zero seria "não avaliou", que é a ausência de linha — permitir zero faria "sem opinião" e
        // "péssima" virarem o mesmo número na média.
        assertThatThrownBy(() -> new Rating(PUBLICACAO, PESSOA, 0, HOJE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 a 5");
        assertThatThrownBy(() -> new Rating(PUBLICACAO, PESSOA, 6, HOJE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Rating(PUBLICACAO, PESSOA, 3, HOJE).value()).isEqualTo(3);
    }

    // --- denúncia ---

    private static AbuseReport denuncia(ReportReason motivo, String texto) {
        return AbuseReport.open(UUID.randomUUID(), PUBLICACAO, PESSOA, motivo, texto, ONTEM);
    }

    @Test
    void denunciarRegistraENaoEscondeNada() {
        // Uma denúncia que tirasse o conteúdo do ar seria uma arma: qualquer um derrubaria a receita de
        // um concorrente escrevendo três linhas.
        var d = denuncia(ReportReason.PLAGIARISM, "é cópia da receita do Bruno");

        assertThat(d.isReviewed()).isFalse();
        assertThat(d.outcome()).isEmpty();
        assertThat(d.reason()).isEqualTo(ReportReason.PLAGIARISM);
    }

    @Test
    void outroMotivoExigeExplicacao() {
        // "Outro" sem texto não é denúncia, é ruído: ninguém revisa o que não foi dito.
        assertThatThrownBy(() -> denuncia(ReportReason.OTHER, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicação");
        assertThat(denuncia(ReportReason.OTHER, "usa marca registrada").note())
                .contains("usa marca registrada");
    }

    @Test
    void osMotivosDaListaDispensamTexto() {
        // Obrigar explicação em "spam" faria a pessoa escrever "spam" no campo de texto.
        assertThat(denuncia(ReportReason.SPAM, null).note()).isEmpty();
    }

    @Test
    void revisarRegistraQuemQuandoEODesfecho() {
        // É o que o critério pede com todas as letras: moderação auditada.
        var d = denuncia(ReportReason.ABUSE, "texto ofensivo");
        d.review(MODERADOR, HOJE, ReportOutcome.UPHELD, "conteúdo removido da vitrine");

        assertThat(d.isReviewed()).isTrue();
        assertThat(d.reviewedBy()).contains(MODERADOR);
        assertThat(d.reviewedAt()).contains(HOJE);
        assertThat(d.outcome()).contains(ReportOutcome.UPHELD);
        assertThat(d.outcomeNote()).contains("conteúdo removido da vitrine");
    }

    @Test
    void aDenunciaImprocedenteNaoEApagada() {
        // Apagá-la faria o mesmo caso voltar do zero — e quem revisa precisa poder ser revisado.
        var d = denuncia(ReportReason.PLAGIARISM, "acho que é cópia");
        d.review(MODERADOR, HOJE, ReportOutcome.DISMISSED, "as receitas são diferentes");

        assertThat(d.note()).contains("acho que é cópia");
        assertThat(d.outcome()).contains(ReportOutcome.DISMISSED);
    }

    @Test
    void naoSeRevisaDuasVezes() {
        var d = denuncia(ReportReason.SPAM, null);
        d.review(MODERADOR, HOJE, ReportOutcome.DISMISSED, null);

        assertThatThrownBy(() -> d.review(MODERADOR, HOJE, ReportOutcome.UPHELD, "mudei de ideia"))
                .isInstanceOf(AlreadyReviewedException.class);
        assertThat(d.outcome()).contains(ReportOutcome.DISMISSED);
    }

    @Test
    void julgarProcedenteNaoApagaNemEscondeSozinho() {
        // A ação sobre o conteúdo é ato separado: encadear automático faria a moderação executar antes
        // de alguém decidir o que fazer.
        var d = denuncia(ReportReason.ABUSE, "ofensivo");
        d.review(MODERADOR, HOJE, ReportOutcome.UPHELD, null);

        assertThat(d.outcome()).contains(ReportOutcome.UPHELD);
        assertThat(d.publicationId()).isEqualTo(PUBLICACAO);
    }
}
