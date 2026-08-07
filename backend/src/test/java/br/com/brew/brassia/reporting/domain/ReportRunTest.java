package br.com.brew.brassia.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.reporting.domain.ReportRun.Delivery;
import br.com.brew.brassia.reporting.domain.SavedReport.ReportFormat;
import br.com.brew.brassia.reporting.domain.SavedReport.ReportKind;
import br.com.brew.brassia.reporting.domain.SavedReport.Schedule;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que uma execução guarda, e por que reentregar não refaz nada (RPT-003). */
class ReportRunTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    @Test
    @DisplayName("execução bem-sucedida nasce com uma entrega pendente por destinatário")
    void nasceComEntregasPendentes() {
        var run = succeeded();

        assertThat(run.deliveryList()).hasSize(2);
        assertThat(run.deliveryList()).allMatch(d -> d.status() == Delivery.Status.PENDING);
        assertThat(run.fullyDelivered()).isFalse();
    }

    @Test
    @DisplayName("reentregar para quem já recebeu não duplica: atualiza e conta a tentativa")
    void reentregarNaoDuplica() {
        var run = succeeded()
                .deliver(ANA, Delivery.Status.DELIVERED, null, NOW)
                .deliver(ANA, Delivery.Status.DELIVERED, null, NOW.plusSeconds(60));

        assertThat(run.deliveryList()).hasSize(2);
        var ana = run.deliveryList().stream().filter(d -> d.userId().equals(ANA)).findFirst()
                .orElseThrow();
        // Duas tentativas, uma linha: é o que impede uma falha parcial de reenviar para a lista toda.
        assertThat(ana.attempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("a entrega falha de um não desfaz a entrega feita do outro")
    void falhaDeUmNaoDesfazAOutro() {
        var run = succeeded()
                .deliver(ANA, Delivery.Status.DELIVERED, null, NOW)
                .deliver(BRUNO, Delivery.Status.REFUSED, "caixa cheia", NOW);

        assertThat(run.fullyDelivered()).isFalse();
        assertThat(run.deliveryList()).anyMatch(d -> d.status() == Delivery.Status.DELIVERED);
        assertThat(run.deliveryList()).anyMatch(d -> "caixa cheia".equals(d.detail()));
    }

    @Test
    @DisplayName("entregar para quem não é destinatário é recusado")
    void entregarParaEstranhoEhRecusado() {
        var run = succeeded();

        assertThatThrownBy(() -> run.deliver(UUID.randomUUID(), Delivery.Status.DELIVERED, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recusa registra o motivo e não produz conteúdo")
    void recusaRegistraOMotivo() {
        var run = ReportRun.refused(report(30), "MANUAL-1", "o dono perdeu a permissão", NOW);

        assertThat(run.succeeded()).isFalse();
        assertThat(run.content()).isNull();
        assertThat(run.refusalReason()).contains("perdeu a permissão");
        // Recusa não tem prazo: não há artefato a expirar.
        assertThat(run.expiresAt()).isNull();
    }

    @Test
    @DisplayName("recusa sem motivo não existe")
    void recusaSemMotivoNaoExiste() {
        assertThatThrownBy(() -> ReportRun.refused(report(30), "MANUAL-1", "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o artefato expira pela retenção da definição")
    void oArtefatoExpiraPelaRetencao() {
        var run = ReportRun.succeeded(report(2), "MANUAL-1", "{}", NOW.minusSeconds(60), NOW, NOW,
                Set.of(ANA));

        assertThat(run.expired(NOW.plus(java.time.Duration.ofDays(1)))).isFalse();
        assertThat(run.expired(NOW.plus(java.time.Duration.ofDays(2)))).isTrue();
    }

    private static ReportRun succeeded() {
        return ReportRun.succeeded(report(30), "MANUAL-1", "{}", NOW.minusSeconds(60), NOW, NOW,
                Set.of(ANA, BRUNO));
    }

    private static SavedReport report(int retentionDays) {
        return SavedReport.define(UUID.randomUUID(), "Painel", ReportKind.DASHBOARD, Map.of(),
                ZoneId.of("America/Sao_Paulo"), ReportFormat.JSON, Schedule.DAILY, retentionDays,
                UUID.randomUUID(), Set.of(ANA, BRUNO), NOW, UUID.randomUUID());
    }
}
