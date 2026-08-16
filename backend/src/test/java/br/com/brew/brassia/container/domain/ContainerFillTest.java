package br.com.brew.brassia.container.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContainerFillTest {

    private static final UUID KEG = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final Instant MARCO = Instant.parse("2026-03-12T14:00:00Z");
    private static final Instant ABRIL = Instant.parse("2026-04-02T09:00:00Z");

    private static ContainerFill enchimento(UUID lote, String codigo, Instant quando) {
        return ContainerFill.record(UUID.randomUUID(), KEG, lote, codigo, new BigDecimal("50"), quando,
                OPERADOR);
    }

    @Test
    void oVinculoGuardaOLoteEOVolume() {
        var f = enchimento(LOTE, "L-2026-031", MARCO);

        assertThat(f.finishedLotId()).isEqualTo(LOTE);
        assertThat(f.lotCode()).isEqualTo("L-2026-031");
        assertThat(f.volumeLiters()).isEqualByComparingTo("50");
        assertThat(f.isCurrent()).isTrue();
    }

    @Test
    void esvaziarNaoApagaOQueEsteveDentro() {
        // A decisão central da história. Um campo "lote atual" sobrescrito responderia "o que está
        // dentro agora" e perderia "o que estava dentro em 12 de março" — que é a pergunta do recall.
        var f = enchimento(LOTE, "L-2026-031", MARCO);
        f.empty(ABRIL);

        assertThat(f.isCurrent()).isFalse();
        assertThat(f.emptiedAt()).contains(ABRIL);
        assertThat(f.finishedLotId()).isEqualTo(LOTE);
        assertThat(f.containedAt(MARCO.plus(Duration.ofDays(3)))).isTrue();
    }

    @Test
    void oIntervaloEFechadoNoInicioEabertoNoFim() {
        // Sem isso, dois enchimentos seguidos responderiam "sim" no mesmo instante de troca, e o recall
        // recolheria dois lotes por causa de um keg.
        var f = enchimento(LOTE, "L-1", MARCO);
        f.empty(ABRIL);

        assertThat(f.containedAt(MARCO)).isTrue();
        assertThat(f.containedAt(MARCO.minusSeconds(1))).isFalse();
        assertThat(f.containedAt(ABRIL)).isFalse();
    }

    @Test
    void oKegComTresLotesRespondePeloDiaCerto() {
        // É o que a genealogia precisa de um vasilhame que vive anos: não "o que tem dentro", e sim "o
        // que tinha dentro naquele dia".
        var primeiro = enchimento(UUID.randomUUID(), "L-1", MARCO);
        primeiro.empty(MARCO.plus(Duration.ofDays(10)));
        var segundo = enchimento(UUID.randomUUID(), "L-2", MARCO.plus(Duration.ofDays(12)));
        segundo.empty(MARCO.plus(Duration.ofDays(20)));
        var terceiro = enchimento(UUID.randomUUID(), "L-3", MARCO.plus(Duration.ofDays(25)));

        var dia = MARCO.plus(Duration.ofDays(15));
        var dentroNaquele = List.of(primeiro, segundo, terceiro).stream()
                .filter(f -> f.containedAt(dia)).toList();

        assertThat(dentroNaquele).hasSize(1);
        assertThat(dentroNaquele.get(0).lotCode()).isEqualTo("L-2");

        // E entre o décimo e o décimo segundo dia ele estava vazio: a lacuna também é resposta.
        assertThat(List.of(primeiro, segundo, terceiro).stream()
                .filter(f -> f.containedAt(MARCO.plus(Duration.ofDays(11)))).toList()).isEmpty();
    }

    @Test
    void esvaziarDuasVezesNaoMudaAData() {
        // Quem clica de novo quer o mesmo resultado, e a data é o fim do período de rastreio.
        var f = enchimento(LOTE, "L-1", MARCO);
        f.empty(ABRIL);
        f.empty(ABRIL.plus(Duration.ofDays(5)));

        assertThat(f.emptiedAt()).contains(ABRIL);
    }

    @Test
    void naoSeEsvaziaAntesDeEncher() {
        // Um intervalo negativo nenhuma consulta de recall consegue interpretar.
        var f = enchimento(LOTE, "L-1", ABRIL);

        assertThatThrownBy(() -> f.empty(MARCO)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void oVolumeEObrigatorioEPositivo() {
        // Volume zero seria um enchimento que não aconteceu, e ele entraria na conta do lote.
        assertThatThrownBy(() -> ContainerFill.record(UUID.randomUUID(), KEG, LOTE, "L-1",
                BigDecimal.ZERO, MARCO, OPERADOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("volume");
    }

    @Test
    void oLoteEQuemEncheuSaoObrigatorios() {
        // Um enchimento sem lote é cerveja sem origem, e sem responsável não há a quem perguntar.
        assertThatThrownBy(() -> ContainerFill.record(UUID.randomUUID(), KEG, null, "L-1",
                BigDecimal.ONE, MARCO, OPERADOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContainerFill.record(UUID.randomUUID(), KEG, LOTE, "L-1",
                BigDecimal.ONE, MARCO, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aRecusaDeConteudoDizOMotivo() {
        // Dois lotes no mesmo vasilhame seria mistura sem registro, e o recall não saberia o que
        // recolher.
        var cheio = FillNotAllowedException.alreadyFull("L-1");

        assertThat(cheio.reasonCode()).isEqualTo("already_full");
        assertThat(cheio.getMessage()).contains("L-1").contains("recall");
        assertThat(FillNotAllowedException.overCapacity().reasonCode()).isEqualTo("over_capacity");
    }

    // --- posição ---

    @Test
    void aPosicaoEHistoricoEAAtualEAUltima() {
        // "Por onde andou" é o que responde quantos dias ele ficou parado num cliente — a conta que a
        // CON-003 vai fazer para cobrar depósito e caçar atraso.
        var deposito = ContainerLocation.at(KEG, LocationKind.WAREHOUSE, "Depósito 1", MARCO);
        var cliente = ContainerLocation.at(KEG, LocationKind.CUSTOMER, "Bar do Bruno", ABRIL);

        assertThat(deposito.kind()).isEqualTo(LocationKind.WAREHOUSE);
        assertThat(cliente.place()).isEqualTo("Bar do Bruno");
        assertThat(cliente.recordedAt()).isAfter(deposito.recordedAt());
    }

    @Test
    void aPosicaoSemLugarNomeadoEEstadoLegitimo() {
        // "Na rua" não tem endereço até a entrega acontecer, e obrigar um texto faria alguém escrever
        // "em trânsito" no campo de lugar.
        var rua = ContainerLocation.at(KEG, LocationKind.IN_TRANSIT, "   ", MARCO);

        assertThat(rua.place()).isNull();
        assertThat(rua.kind()).isEqualTo(LocationKind.IN_TRANSIT);
    }
}
