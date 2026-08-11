package br.com.brew.brassia.traceability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que o simulado mede, e o que ele se recusa a medir (FDS-004). */
class RecallDrillTest {

    private static final Instant START = Instant.parse("2026-08-05T09:00:00Z");
    private static final Instant END = Instant.parse("2026-08-05T11:30:00Z");
    private static final Node LOTE = new Node(NodeType.BATCH, UUID.randomUUID(), "LOTE-100");

    @Test
    @DisplayName("o percentual localizado é o número do relatório")
    void percentualLocalizado() {
        var drill = start();

        drill.finish(UUID.randomUUID(), 200, 150, 3, 1, "três destinos visitados", null, null, END);

        assertThat(drill.locatedPercent()).isEqualTo(75);
        assertThat(drill.unitsInScope()).isEqualTo(200);
        assertThat(drill.gapsFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("escopo vazio não vira 100%: não achar o que não existe não é cobertura")
    void escopoVazioNaoEhCobertura() {
        var drill = start();

        drill.finish(UUID.randomUUID(), 0, 0, 0, 0, "nada saiu deste lote", null, null, END);

        assertThat(drill.locatedPercent()).isNull();
    }

    @Test
    @DisplayName("localizar mais do que existe é recusado — é erro de contagem, não excelência")
    void naoLocalizaMaisDoQueExiste() {
        var drill = start();

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 100, 120, 1, 0, "achei demais", null, null, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drill.running()).isTrue();
    }

    @Test
    @DisplayName("o tempo medido é o da cervejaria, do início ao encerramento")
    void mediaOTempoDaCervejaria() {
        var drill = start();

        // Enquanto corre, o tempo é o de agora.
        assertThat(drill.elapsed(START.plusSeconds(600)).toMinutes()).isEqualTo(10);

        drill.finish(UUID.randomUUID(), 200, 200, 2, 0, "tudo localizado", "nenhuma", null, END);

        // Encerrado, o tempo para de contar: o relatório é sobre aquele exercício.
        assertThat(drill.elapsed(Instant.parse("2026-08-06T09:00:00Z")).toMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("encerrar exige resumo e só acontece uma vez")
    void encerraUmaVezComResumo() {
        var drill = start();

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "  ", null, null, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drill.running()).isTrue();

        drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "ok", null, null, END);

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "de novo", null, null, END))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RecallDrill start() {
        return RecallDrill.start(UUID.randomUUID(), "SIM-2026-0001", LOTE, "exercício trimestral",
                UUID.randomUUID(), START);
    }

    @Test
    @DisplayName("A AÇÃO OU É TEXTO OU É ITEM DE CAPA, nunca as duas")
    void textoEcapaSaoExcludentes() {
        // Os dois juntos deixariam quem lê o relatório sem saber qual é a ação de verdade: a escrita no
        // texto, sem dono nem prazo, ou a que tem os dois no CAPA.
        var drill = start();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 200, 150,
                        3, 1, "resumo", "revisar contatos", UUID.randomUUID(), END))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não as duas");
    }

    @Test
    @DisplayName("o simulado guarda a NC onde as ações viraram CAPA")
    void guardaAncoraDoCapa() {
        var drill = start();
        var nc = UUID.randomUUID();

        drill.finish(UUID.randomUUID(), 200, 150, 3, 1, "resumo", null, nc, END);

        assertThat(drill.nonConformityId()).contains(nc);
        assertThat(drill.correctiveActions()).isNull();
    }

    @Test
    @DisplayName("simulado sem lacuna encerra sem NC e sem texto: não há ação a tomar")
    void semLacunaNaoExigeAcao() {
        var drill = start();

        drill.finish(UUID.randomUUID(), 200, 200, 3, 0, "tudo localizado", null, null, END);

        assertThat(drill.nonConformityId()).isEmpty();
        assertThat(drill.correctiveActions()).isNull();
    }
}