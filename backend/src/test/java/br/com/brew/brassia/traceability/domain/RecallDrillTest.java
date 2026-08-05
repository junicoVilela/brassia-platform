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

        drill.finish(UUID.randomUUID(), 200, 150, 3, 1, "três destinos visitados", null, END);

        assertThat(drill.locatedPercent()).isEqualTo(75);
        assertThat(drill.unitsInScope()).isEqualTo(200);
        assertThat(drill.gapsFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("escopo vazio não vira 100%: não achar o que não existe não é cobertura")
    void escopoVazioNaoEhCobertura() {
        var drill = start();

        drill.finish(UUID.randomUUID(), 0, 0, 0, 0, "nada saiu deste lote", null, END);

        assertThat(drill.locatedPercent()).isNull();
    }

    @Test
    @DisplayName("localizar mais do que existe é recusado — é erro de contagem, não excelência")
    void naoLocalizaMaisDoQueExiste() {
        var drill = start();

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 100, 120, 1, 0, "achei demais", null, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drill.running()).isTrue();
    }

    @Test
    @DisplayName("o tempo medido é o da cervejaria, do início ao encerramento")
    void mediaOTempoDaCervejaria() {
        var drill = start();

        // Enquanto corre, o tempo é o de agora.
        assertThat(drill.elapsed(START.plusSeconds(600)).toMinutes()).isEqualTo(10);

        drill.finish(UUID.randomUUID(), 200, 200, 2, 0, "tudo localizado", "nenhuma", END);

        // Encerrado, o tempo para de contar: o relatório é sobre aquele exercício.
        assertThat(drill.elapsed(Instant.parse("2026-08-06T09:00:00Z")).toMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("encerrar exige resumo e só acontece uma vez")
    void encerraUmaVezComResumo() {
        var drill = start();

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "  ", null, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drill.running()).isTrue();

        drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "ok", null, END);

        assertThatThrownBy(() -> drill.finish(UUID.randomUUID(), 10, 10, 1, 0, "de novo", null, END))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RecallDrill start() {
        return RecallDrill.start(UUID.randomUUID(), "SIM-2026-0001", LOTE, "exercício trimestral",
                UUID.randomUUID(), START);
    }
}
