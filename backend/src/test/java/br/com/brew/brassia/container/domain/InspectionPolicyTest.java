package br.com.brew.brassia.container.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionPolicyTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final Instant INSPECAO = Instant.parse("2026-03-10T10:00:00Z");

    private static InspectionPolicy politica(int meses) {
        return new InspectionPolicy(UUID.randomUUID(), CERVEJARIA, ContainerKind.KEG, meses, null,
                UUID.randomUUID());
    }

    @Test
    void aValidadeSugeridaContaDaInspecaoENaoDeHoje() {
        // Uma inspeção lançada com atraso vale a partir de quando aconteceu, e não de quando alguém
        // digitou — senão registrar tarde estenderia o prazo de graça.
        var sugerida = politica(60).suggestedValidUntil(INSPECAO);

        assertThat(sugerida).isEqualTo(Instant.parse("2031-03-10T10:00:00Z"));
    }

    @Test
    void oIntervaloEDaCasaENaoDoSistema() {
        // O sistema não traz número de fábrica: escrever "cinco anos" aqui faria ele afirmar conformidade
        // que ninguém verificou, num equipamento cuja falha é física.
        assertThat(politica(60).intervalMonths()).isEqualTo(60);
        assertThat(politica(12).suggestedValidUntil(INSPECAO))
                .isEqualTo(Instant.parse("2027-03-10T10:00:00Z"));
    }

    @Test
    void intervaloZeroNaoEPolitica() {
        // "Inspecionar sempre" na prática é não ter política, e a ausência já se representa não
        // cadastrando.
        assertThatThrownBy(() -> politica(0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos um mês");
        assertThatThrownBy(() -> politica(-6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPoliticaEPorTipoDeVasilhame() {
        // Keg de pressão e growler não seguem o mesmo prazo, e uma política única obrigaria a casa a
        // adotar o menor dos dois para todos.
        var keg = new InspectionPolicy(UUID.randomUUID(), CERVEJARIA, ContainerKind.KEG, 60, null,
                UUID.randomUUID());
        var growler = new InspectionPolicy(UUID.randomUUID(), CERVEJARIA, ContainerKind.GROWLER, 12,
                "vidro: inspeção anual", UUID.randomUUID());

        assertThat(keg.kind()).isEqualTo(ContainerKind.KEG);
        assertThat(growler.kind()).isEqualTo(ContainerKind.GROWLER);
        assertThat(growler.note()).contains("vidro");
    }

    @Test
    void aSugestaoNaoEAutoridade() {
        // A inspeção que encontra um problema pode encurtar o prazo: a validade continua sendo o que o
        // inspetor informar, e a política só propõe.
        var politica = politica(60);
        var encurtada = INSPECAO.plus(Duration.ofDays(30));

        var registro = new ContainerInspection(INSPECAO, encurtada, UUID.randomUUID(),
                "válvula com folga: revisar em um mês");

        assertThat(registro.validUntil()).isEqualTo(encurtada);
        assertThat(registro.validUntil()).isBefore(politica.suggestedValidUntil(INSPECAO));
    }
}
