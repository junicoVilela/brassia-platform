package br.com.brew.brassia.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** O que a definição registra e o que ela recusa (RPT-003). */
class SavedReportTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    @DisplayName("a definição nasce registrando versão, filtros, tenant, fuso, formato e retenção")
    void aDefinicaoRegistraTudoQueOCriterioPede() {
        var report = define(Schedule.MONTHLY, 90);

        assertThat(report.definitionVersion()).isEqualTo(1);
        assertThat(report.filters()).containsEntry("group", "COST");
        assertThat(report.breweryId()).isNotNull();
        assertThat(report.timezone()).isEqualTo(SAO_PAULO);
        assertThat(report.format()).isEqualTo(ReportFormat.JSON);
        assertThat(report.retentionDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("redefinir sobe a versão: execução antiga não pode dizer que saiu do recorte novo")
    void redefinirSobeAVersao() {
        var report = define(Schedule.DAILY, 30);

        var updated = report.redefine(Map.of("group", "PRODUCTION"), SAO_PAULO, Schedule.WEEKLY, 15,
                Set.of());

        assertThat(updated.definitionVersion()).isEqualTo(2);
        assertThat(updated.filters()).containsEntry("group", "PRODUCTION");
        assertThat(updated.schedule()).isEqualTo(Schedule.WEEKLY);
        // O que não se redefine: dono e tipo continuam os mesmos, senão seria outro relatório.
        assertThat(updated.ownerUserId()).isEqualTo(report.ownerUserId());
        assertThat(updated.kind()).isEqualTo(report.kind());
    }

    @Test
    @DisplayName("a permissão exigida é a do tipo de relatório, e é a que o dono precisa ter")
    void aPermissaoExigidaVemDoTipo() {
        assertThat(define(Schedule.DAILY, 30).requiredPermission())
                .isEqualTo("reporting.dashboard.read");
    }

    @Test
    @DisplayName("a retenção decide o prazo do artefato")
    void aRetencaoDecideOPrazo() {
        var report = define(Schedule.DAILY, 7);

        assertThat(report.expiryFrom(NOW)).isEqualTo(NOW.plus(java.time.Duration.ofDays(7)));
    }

    @Test
    @DisplayName("retenção fora da faixa é recusada")
    void retencaoForaDaFaixaEhRecusada() {
        assertThatThrownBy(() -> define(Schedule.DAILY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> define(Schedule.DAILY, 4000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nome em branco é recusado")
    void nomeEmBrancoEhRecusado() {
        assertThatThrownBy(() -> SavedReport.define(UUID.randomUUID(), "  ", ReportKind.DASHBOARD,
                Map.of(), SAO_PAULO, ReportFormat.JSON, Schedule.DAILY, 30, UUID.randomUUID(),
                Set.of(), NOW, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("definição manual não é programada")
    void manualNaoEhProgramada() {
        assertThat(define(Schedule.MANUAL, 30).scheduled()).isFalse();
        assertThat(define(Schedule.DAILY, 30).scheduled()).isTrue();
    }

    private static SavedReport define(Schedule schedule, int retentionDays) {
        return SavedReport.define(UUID.randomUUID(), "Painel mensal", ReportKind.DASHBOARD,
                Map.of("group", "COST"), SAO_PAULO, ReportFormat.JSON, schedule, retentionDays,
                UUID.randomUUID(), Set.of(UUID.randomUUID()), NOW, UUID.randomUUID());
    }
}
