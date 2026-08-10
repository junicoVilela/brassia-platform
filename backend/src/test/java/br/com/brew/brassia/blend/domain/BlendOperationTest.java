package br.com.brew.brassia.blend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * União e divisão de volume (BLD-001).
 *
 * <p>O que estes testes fixam: o balanço fecha na <em>simulação</em>, e a genealogia só passa a valer
 * depois da execução — antes disso nenhuma cerveja se tocou.
 */
class BlendOperationTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T12:00:00Z");

    private static VolumeMovement mov(String litros) {
        return new VolumeMovement(UUID.randomUUID(), new BigDecimal(litros));
    }

    private static BlendOperation uniao(List<VolumeMovement> entradas, List<VolumeMovement> saidas,
            String perda) {
        return BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, entradas, saidas,
                new BigDecimal(perda), "Aproveitamento de sobra de tanque", ATOR, AGORA);
    }

    @Nested
    @DisplayName("o balanço fecha")
    class Balanco {

        @Test
        @DisplayName("entrada igual a saída mais perda declarada")
        void fechaComPerda() {
            var op = uniao(List.of(mov("400"), mov("200")), List.of(mov("588")), "12");

            assertThat(op.status()).isEqualTo(BlendStatus.SIMULATED);
            assertThat(op.inputLiters()).isEqualByComparingTo("600");
            assertThat(op.outputLiters()).isEqualByComparingTo("588");
        }

        @Test
        @DisplayName("CERVEJA SUMINDO É RECUSADA, e o erro diz quanto falta explicar")
        void cervejaSumindo() {
            // Aceitar em silêncio criaria volume do nada — e volume do nada vira cerveja envasada que a
            // rastreabilidade não sabe de onde veio.
            assertThatExceptionOfType(UnbalancedBlendException.class)
                    .isThrownBy(() -> uniao(List.of(mov("400"), mov("200")), List.of(mov("500")), "0"))
                    .satisfies(e -> {
                        assertThat(e.difference()).isEqualByComparingTo("100");
                        assertThat(e.inputLiters()).isEqualByComparingTo("600");
                    });
        }

        @Test
        @DisplayName("cerveja aparecendo também é recusada")
        void cervejaAparecendo() {
            assertThatExceptionOfType(UnbalancedBlendException.class)
                    .isThrownBy(() -> uniao(List.of(mov("300"), mov("200")), List.of(mov("560")), "0"))
                    .satisfies(e -> assertThat(e.difference()).isEqualByComparingTo("-60"));
        }

        @Test
        @DisplayName("diferença dentro da tolerância passa")
        void toleranciaDeInstrumentacao() {
            // Exigir igualdade exata recusaria operações corretas por arredondamento — e treinaria quem
            // opera a inflar a perda declarada até a conta passar.
            var op = uniao(List.of(mov("400.00"), mov("200.00")), List.of(mov("599.95")), "0");

            assertThat(op.outputLiters()).isEqualByComparingTo("599.95");
        }

        @Test
        @DisplayName("diferença logo acima da tolerância não passa")
        void acimaDaTolerancia() {
            assertThatExceptionOfType(UnbalancedBlendException.class).isThrownBy(
                    () -> uniao(List.of(mov("400.00"), mov("200.00")), List.of(mov("599.80")), "0"));
        }

        @Test
        @DisplayName("perda negativa é recusada")
        void perdaNegativa() {
            // Seria cerveja aparecendo do nada com nome de perda.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> uniao(List.of(mov("300"), mov("300")), List.of(mov("620")), "-20"));
        }

        @Test
        @DisplayName("volume não positivo é recusado no movimento")
        void volumeNaoPositivo() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new VolumeMovement(UUID.randomUUID(), BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("forma da operação")
    class Forma {

        @Test
        @DisplayName("união exige duas origens; divisão exige dois destinos")
        void formaCoerente() {
            // Sem isto, "MERGE" com uma entrada e três saídas apareceria como união no histórico,
            // descrevendo o oposto do que houve.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> uniao(List.of(mov("600")), List.of(mov("600")), "0"));

            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.SPLIT, List.of(mov("300"), mov("300")),
                    List.of(mov("300"), mov("300")), BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("divisão de um lote em dois é aceita")
        void divisaoValida() {
            var op = BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.SPLIT,
                    List.of(mov("600")), List.of(mov("300"), mov("295")), new BigDecimal("5"),
                    "Separar para dry hopping distinto", ATOR, AGORA);

            assertThat(op.kind()).isEqualTo(BlendKind.SPLIT);
        }

        @Test
        @DisplayName("O MESMO LOTE NÃO PODE SER ORIGEM E DESTINO")
        void semCiclo() {
            // Fecharia o balanço consigo mesmo e criaria uma aresta de um lote para ele próprio — um
            // ciclo que trava qualquer travessia de recall.
            var lote = UUID.randomUUID();
            var entrada = new VolumeMovement(lote, new BigDecimal("300"));
            var outra = mov("300");
            var saida = new VolumeMovement(lote, new BigDecimal("600"));

            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, List.of(entrada, outra),
                    List.of(saida), BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("lote repetido do mesmo lado é recusado")
        void loteRepetido() {
            var lote = UUID.randomUUID();
            var a = new VolumeMovement(lote, new BigDecimal("300"));
            var b = new VolumeMovement(lote, new BigDecimal("300"));

            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, List.of(a, b),
                    List.of(mov("600")), BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("motivo vazio é recusado")
        void motivoVazio() {
            // Blend sem motivo é decisão sem rastro: meses depois ninguém sabe se foi correção de desvio,
            // ajuste de estilo ou aproveitamento de sobra.
            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, List.of(mov("300"), mov("300")),
                    List.of(mov("600")), BigDecimal.ZERO, "   ", ATOR, AGORA));
        }
    }

    @Nested
    @DisplayName("ciclo de vida e genealogia")
    class CicloDeVida {

        private BlendOperation simulada() {
            return uniao(List.of(mov("400"), mov("200")), List.of(mov("600")), "0");
        }

        @Test
        @DisplayName("A GENEALOGIA SÓ VALE DEPOIS DE EXECUTAR")
        void genealogiaApenasExecutada() {
            // Um recall que alcança lotes que nunca se tocaram é descartado por quem o recebe — tão
            // inútil quanto um recall que falta.
            var op = simulada();
            assertThat(op.contributesLineage()).isFalse();

            op.approve(ATOR, AGORA);
            assertThat(op.contributesLineage()).isFalse();

            op.execute(ATOR, AGORA);
            assertThat(op.contributesLineage()).isTrue();
        }

        @Test
        @DisplayName("não se executa o que não foi aprovado")
        void executarSemAprovar() {
            var op = simulada();

            assertThatExceptionOfType(BlendOperation.IllegalBlendTransitionException.class)
                    .isThrownBy(() -> op.execute(ATOR, AGORA))
                    .satisfies(e -> assertThat(e.current()).isEqualTo(BlendStatus.SIMULATED));
        }

        @Test
        @DisplayName("não se aprova duas vezes")
        void aprovarDuasVezes() {
            var op = simulada();
            op.approve(ATOR, AGORA);

            assertThatExceptionOfType(BlendOperation.IllegalBlendTransitionException.class)
                    .isThrownBy(() -> op.approve(ATOR, AGORA));
        }

        @Test
        @DisplayName("executada não se descarta")
        void executadaNaoDescarta() {
            // Apagar deixaria dois lotes com volume alterado e nenhuma explicação.
            var op = simulada();
            op.approve(ATOR, AGORA);
            op.execute(ATOR, AGORA);

            assertThatExceptionOfType(BlendOperation.IllegalBlendTransitionException.class)
                    .isThrownBy(op::discard);
        }

        @Test
        @DisplayName("simulada pode ser descartada e some da genealogia")
        void descartada() {
            var op = simulada();

            op.discard();

            assertThat(op.status()).isEqualTo(BlendStatus.DISCARDED);
            assertThat(op.contributesLineage()).isFalse();
        }

        @Test
        @DisplayName("quem aprovou e quem executou ficam registrados")
        void autoresRegistrados() {
            var aprovador = UUID.randomUUID();
            var operador = UUID.randomUUID();
            var op = simulada();

            op.approve(aprovador, AGORA);
            op.execute(operador, AGORA.plusSeconds(600));

            assertThat(op.approvedBy()).contains(aprovador);
            assertThat(op.executedBy()).contains(operador);
            assertThat(op.executedAt()).contains(AGORA.plusSeconds(600));
        }
    }
}
