package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NonConformityTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final UUID DESVIO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-03T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.parse("2026-08-03");

    private static NonConformity aberta() {
        return NonConformity.open(BREWERY, "NC-001", "pH fora da faixa no lote L-2608",
                "Medição de pH acusou 6,2 contra o teto de 5,5", NonConformitySource.DEVIATION, DESVIO,
                Severity.MAJOR, HOJE.plusDays(1), HOJE.plusDays(5), HOJE.plusDays(30), AGORA, ATOR);
    }

    /** NC levada até o ponto de ter uma ação concluída, pronta para verificação. */
    private static NonConformity comAcaoConcluida() {
        var nc = aberta();
        nc.contain("Lote segregado no tanque FV-03", AGORA, ATOR);
        nc.investigate("Água de diluição com alcalinidade acima do normal", "5 porquês", AGORA, ATOR);
        var acao = nc.planAction(CapaActionKind.CORRECTIVE, "Corrigir o pH do lote com ácido lático",
                "Brassista", HOJE.plusDays(2));
        nc.completeAction(acao.id(), AGORA);
        return nc;
    }

    // --- ordem das fases ---

    @Test
    void nasceAbertaSemNenhumaFaseCumprida() {
        var nc = aberta();

        assertThat(nc.status()).isEqualTo(NonConformityStatus.OPEN);
        assertThat(nc.containment()).isEmpty();
        assertThat(nc.investigation()).isEmpty();
        assertThat(nc.actions()).isEmpty();
        assertThat(nc.verifications()).isEmpty();
    }

    @Test
    void naoInvestigaOQueNaoConteve() {
        var nc = aberta();

        assertThatThrownBy(() -> nc.investigate("causa", "método", AGORA, ATOR))
                .isInstanceOf(PhaseOutOfOrderException.class)
                .satisfies(e -> {
                    var ex = (PhaseOutOfOrderException) e;
                    assertThat(ex.current()).isEqualTo(NonConformityStatus.OPEN);
                    assertThat(ex.attempted()).isEqualTo("investigação");
                });
    }

    @Test
    void naoAgeSemCausaRaiz() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);

        assertThatThrownBy(() -> nc.planAction(CapaActionKind.CORRECTIVE, "algo", "alguém", HOJE.plusDays(1)))
                .isInstanceOf(PhaseOutOfOrderException.class);
    }

    @Test
    void naoVerificaSemAcao() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);
        nc.investigate("causa", "5 porquês", AGORA, ATOR);

        assertThatThrownBy(() -> nc.verify(true, "evidência", AGORA, ATOR))
                .isInstanceOf(PhaseOutOfOrderException.class);
    }

    @Test
    void naoVerificaEficaciaDeAcaoQueNinguemConcluiu() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);
        nc.investigate("causa", "5 porquês", AGORA, ATOR);
        nc.planAction(CapaActionKind.CORRECTIVE, "Ajustar", "Brassista", HOJE.plusDays(2));

        assertThatThrownBy(() -> nc.verify(true, "evidência", AGORA, ATOR))
                .isInstanceOf(VerificationRequiredException.class)
                .hasMessageContaining("antes de concluir qualquer ação");
    }

    @Test
    void aContencaoNaoSeRepete() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);

        assertThatThrownBy(() -> nc.contain("de novo", AGORA, ATOR))
                .isInstanceOf(PhaseOutOfOrderException.class);
    }

    // --- verificação e encerramento ---

    @Test
    void verificacaoEficazHabilitaOEncerramento() {
        var nc = comAcaoConcluida();

        nc.verify(true, "Três lotes seguintes dentro da faixa", AGORA, ATOR);

        assertThat(nc.status()).isEqualTo(NonConformityStatus.VERIFIED);

        nc.close(AGORA, ATOR);

        assertThat(nc.status()).isEqualTo(NonConformityStatus.CLOSED);
        assertThat(nc.closedBy()).isEqualTo(ATOR);
    }

    @Test
    void verificacaoIneficazDevolveAFaseDeAcao() {
        // Fechar com verificação negativa produziria registro de solução que nunca existiu.
        var nc = comAcaoConcluida();

        nc.verify(false, "O lote seguinte repetiu o desvio", AGORA, ATOR);

        assertThat(nc.status()).isEqualTo(NonConformityStatus.INVESTIGATED);
        assertThat(nc.verifications()).hasSize(1);
        assertThatThrownBy(() -> nc.close(AGORA, ATOR)).isInstanceOf(VerificationRequiredException.class);

        // E aceita ação nova, que é justamente o que a verificação negativa exige.
        var nova = nc.planAction(CapaActionKind.PREVENTIVE, "Tratar a água de diluição", "Qualidade",
                HOJE.plusDays(10));
        assertThat(nc.status()).isEqualTo(NonConformityStatus.ACTION_PLANNED);
        assertThat(nova.kind()).isEqualTo(CapaActionKind.PREVENTIVE);
    }

    @Test
    void encerrarSemVerificacaoEhRecusado() {
        var nc = comAcaoConcluida();

        assertThatThrownBy(() -> nc.close(AGORA, ATOR))
                .isInstanceOf(VerificationRequiredException.class)
                .hasMessageContaining("resultado positivo");
    }

    @Test
    void aVerificacaoNegativaFicaNoHistorico() {
        var nc = comAcaoConcluida();
        nc.verify(false, "repetiu", AGORA, ATOR);
        var acao = nc.planAction(CapaActionKind.PREVENTIVE, "Tratar a água", "Qualidade", HOJE.plusDays(10));
        nc.completeAction(acao.id(), AGORA);
        nc.verify(true, "cinco lotes conformes", AGORA, ATOR);

        assertThat(nc.verifications()).hasSize(2);
        assertThat(nc.verifications().get(0).effective()).isFalse();
        assertThat(nc.verifications().get(1).effective()).isTrue();
    }

    @Test
    void encerrarNaoSeRepete() {
        var nc = comAcaoConcluida();
        nc.verify(true, "ok", AGORA, ATOR);
        nc.close(AGORA, ATOR);

        assertThatThrownBy(() -> nc.close(AGORA, ATOR)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encerrarEntregaODesvioParaFechar() {
        var nc = comAcaoConcluida();
        assertThat(nc.deviationToClose()).isEmpty();

        nc.verify(true, "ok", AGORA, ATOR);
        nc.close(AGORA, ATOR);

        assertThat(nc.deviationToClose()).contains(DESVIO);
    }

    // --- prazos ---

    @Test
    void oPrazoVencidoEhDerivadoDaData() {
        var nc = aberta();

        assertThat(nc.overdue(HOJE)).isFalse();
        // Passou o prazo de contenção sem conter.
        assertThat(nc.overduePhases(HOJE.plusDays(2))).contains("containment");
        assertThat(nc.overduePhases(HOJE.plusDays(10))).contains("containment", "investigation");
    }

    @Test
    void faseCumpridaDeixaDeAtrasar() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);

        assertThat(nc.overduePhases(HOJE.plusDays(2))).doesNotContain("containment");
    }

    @Test
    void acaoAtrasadaApareceNaLista() {
        var nc = aberta();
        nc.contain("Lote segregado", AGORA, ATOR);
        nc.investigate("causa", "5 porquês", AGORA, ATOR);
        var acao = nc.planAction(CapaActionKind.CORRECTIVE, "Ajustar", "Brassista", HOJE.plusDays(2));

        assertThat(nc.overduePhases(HOJE.plusDays(3))).contains("action:" + acao.id());

        nc.completeAction(acao.id(), AGORA);
        assertThat(nc.overduePhases(HOJE.plusDays(3))).doesNotContain("action:" + acao.id());
    }

    @Test
    void naoConformidadeEncerradaNaoTemFaseAtrasada() {
        var nc = comAcaoConcluida();
        nc.verify(true, "ok", AGORA, ATOR);
        nc.close(AGORA, ATOR);

        assertThat(nc.overduePhases(HOJE.plusYears(1))).isEmpty();
    }

    @Test
    void osPrazosSeguemAOrdemDasFases() {
        assertThatThrownBy(() -> NonConformity.open(BREWERY, "NC-002", "t", "d",
                NonConformitySource.AUDIT, null, Severity.MINOR,
                HOJE.plusDays(10), HOJE.plusDays(5), HOJE.plusDays(30), AGORA, ATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordem das fases");
    }

    // --- origem ---

    @Test
    void origemDesvioExigeApontarODesvio() {
        assertThatThrownBy(() -> NonConformity.open(BREWERY, "NC-003", "t", "d",
                NonConformitySource.DEVIATION, null, Severity.MAJOR,
                HOJE.plusDays(1), HOJE.plusDays(5), HOJE.plusDays(30), AGORA, ATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apontar o desvio");
    }

    @Test
    void origemSemDesvioNaoExigeVinculo() {
        var nc = NonConformity.open(BREWERY, "NC-004", "Reclamação de turbidez",
                "Cliente relatou turvação em garrafa", NonConformitySource.COMPLAINT, null, Severity.MINOR,
                HOJE.plusDays(1), HOJE.plusDays(5), HOJE.plusDays(30), AGORA, ATOR);

        assertThat(nc.deviationId()).isEmpty();
        assertThat(nc.source()).isEqualTo(NonConformitySource.COMPLAINT);
    }

    @Test
    void aAcaoSeparaCorretivaDePreventiva() {
        var nc = comAcaoConcluida();
        nc.verify(false, "repetiu", AGORA, ATOR);
        nc.planAction(CapaActionKind.PREVENTIVE, "Tratar a água", "Qualidade", HOJE.plusDays(10));

        assertThat(nc.actions()).extracting(CapaAction::kind)
                .containsExactly(CapaActionKind.CORRECTIVE, CapaActionKind.PREVENTIVE);
    }

    @Test
    void aAcaoNaoSeConcluiDuasVezes() {
        var nc = comAcaoConcluida();
        var acao = nc.actions().get(0);

        assertThatThrownBy(() -> nc.completeAction(acao.id(), AGORA))
                .isInstanceOf(IllegalStateException.class);
    }
}
