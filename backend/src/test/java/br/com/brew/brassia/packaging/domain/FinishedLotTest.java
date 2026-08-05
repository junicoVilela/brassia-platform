package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lote de produto acabado (TRC-001-B): o que sai da linha e o que um recall recolhe. */
class FinishedLotTest {

    private static final Instant AT = Instant.parse("2026-08-05T12:00:00Z");
    private static final LocalDate ON = LocalDate.of(2026, 8, 5);

    private static PackagingRun run(int produced, int rejected) {
        return PackagingRun.execute(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("355"), new BigDecimal("284"), produced, rejected, null, AT, UUID.randomUUID());
    }

    @Test
    void contaSoAsUnidadesBoas() {
        var lot = FinishedLot.from(run(780, 12), "LOTE-100", UUID.randomUUID(), 1, ON);

        // Rejeito consumiu embalagem e não virou produto: incluí-lo faria o recall procurar latas
        // que ninguém pode devolver.
        assertThat(lot.units()).isEqualTo(780);
        assertThat(lot.volumeLiters()).isEqualByComparingTo("276.9");
    }

    @Test
    void oCodigoLevaDeVoltaAoLoteDeProducao() {
        var lot = FinishedLot.from(run(780, 0), "LOTE-100", UUID.randomUUID(), 1, ON);

        assertThat(lot.code()).isEqualTo("LOTE-100/1");
        assertThat(lot.batchCode()).isEqualTo("LOTE-100");
    }

    @Test
    void cadaEnvaseDoMesmoLoteTemIdentidadePropria() {
        var container = UUID.randomUUID();

        var primeiro = FinishedLot.from(run(780, 0), "LOTE-100", container, 1, ON);
        var segundo = FinishedLot.from(run(400, 0), "LOTE-100", container, 2, ON);

        // Foram latas diferentes, em momentos diferentes: um recall pode atingir só uma delas.
        assertThat(primeiro.code()).isNotEqualTo(segundo.code());
        assertThat(segundo.code()).isEqualTo("LOTE-100/2");
    }

    @Test
    void guardaAOrigemInteiraParaARastreabilidade() {
        var run = run(780, 12);

        var lot = FinishedLot.from(run, "LOTE-100", UUID.randomUUID(), 1, ON);

        assertThat(lot.runId()).isEqualTo(run.id());
        assertThat(lot.planId()).isEqualTo(run.planId());
        assertThat(lot.batchId()).isEqualTo(run.batchId());
    }

    @Test
    void envaseSemUnidadeBoaNaoGeraLote() {
        // Uma execução em que tudo foi rejeitado não produziu cerveja para recolher.
        assertThatThrownBy(() -> FinishedLot.from(run(0, 50), "LOTE-100", UUID.randomUUID(), 1, ON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem unidades boas");
    }

    @Test
    void ordemDeEnvaseComecaEmUm() {
        assertThatThrownBy(() -> FinishedLot.from(run(10, 0), "LOTE-100", UUID.randomUUID(), 0, ON))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
