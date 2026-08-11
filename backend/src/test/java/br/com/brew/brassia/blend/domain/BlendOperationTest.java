package br.com.brew.brassia.blend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
    private static final List<PlannedOutput> NENHUM_RESULTADO = List.of();

    private static VolumeMovement mov(String litros) {
        return new VolumeMovement(UUID.randomUUID(), new BigDecimal(litros));
    }

    private static BlendOperation uniao(List<VolumeMovement> entradas, List<VolumeMovement> saidas,
            String perda) {
        return BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, entradas, saidas,
                NENHUM_RESULTADO, new BigDecimal(perda), "Aproveitamento de sobra de tanque", ATOR, AGORA);
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
                    List.of(mov("300"), mov("300")), NENHUM_RESULTADO, BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("divisão de um lote em dois é aceita")
        void divisaoValida() {
            var op = BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.SPLIT,
                    List.of(mov("600")), List.of(mov("300"), mov("295")), NENHUM_RESULTADO, new BigDecimal("5"),
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
                    List.of(saida), NENHUM_RESULTADO, BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("lote repetido do mesmo lado é recusado")
        void loteRepetido() {
            var lote = UUID.randomUUID();
            var a = new VolumeMovement(lote, new BigDecimal("300"));
            var b = new VolumeMovement(lote, new BigDecimal("300"));

            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, List.of(a, b),
                    List.of(mov("600")), NENHUM_RESULTADO, BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("motivo vazio é recusado")
        void motivoVazio() {
            // Blend sem motivo é decisão sem rastro: meses depois ninguém sabe se foi correção de desvio,
            // ajuste de estilo ou aproveitamento de sobra.
            assertThatIllegalArgumentException().isThrownBy(() -> BlendOperation.simulate(
                    UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE, List.of(mov("300"), mov("300")),
                    List.of(mov("600")), NENHUM_RESULTADO, BigDecimal.ZERO, "   ", ATOR, AGORA));
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

    @Nested
    @DisplayName("o resultado é um lote novo (DEC-BLD-003)")
    class LoteDeResultado {

        private static final UUID RECEITA = UUID.randomUUID();
        private static final UUID TANQUE = UUID.randomUUID();

        private BlendOperation uniaoParaLoteNovo() {
            return BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE,
                    List.of(mov("400"), mov("200")), List.of(),
                    List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("588"))),
                    new BigDecimal("12"), "União para lote novo", ATOR, AGORA);
        }

        @Test
        @DisplayName("DUAS ORIGENS PODEM VIRAR UM LOTE NOVO, sem nenhuma saída pré-existente")
        void saidaSoPlanejada() {
            // É a operação que a DEC-BLD-003 destravou, e a que a forma antiga recusava: contar apenas
            // lotes existentes como saída tornaria inexprimível justamente o caso pedido.
            var op = uniaoParaLoteNovo();

            assertThat(op.outputs()).isEmpty();
            assertThat(op.plannedOutputs()).hasSize(1);
            assertThat(op.outputLiters()).isEqualByComparingTo("588");
        }

        @Test
        @DisplayName("o balanço soma o lote novo junto com os que já existem")
        void balancoSomaOsDois() {
            // Sem somar os dois, uma união de 600 L com 300 L indo para lote existente e 288 L para lote
            // novo pareceria estar perdendo 288 L sem ninguém declarar.
            var op = BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE,
                    List.of(mov("400"), mov("200")), List.of(mov("300")),
                    List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("288"))),
                    new BigDecimal("12"), "Parte para tanque existente, parte para lote novo", ATOR, AGORA);

            assertThat(op.outputLiters()).isEqualByComparingTo("588");
        }

        @Test
        @DisplayName("saída planejada que não fecha o balanço é recusada igual às outras")
        void planejadaEntraNoBalanco() {
            assertThatExceptionOfType(UnbalancedBlendException.class).isThrownBy(
                    () -> BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE,
                            List.of(mov("400"), mov("200")), List.of(),
                            List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("500"))),
                            BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("divisão em dois lotes novos é aceita; a forma conta saída planejada")
        void divisaoEmDoisLotesNovos() {
            var op = BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.SPLIT,
                    List.of(mov("600")), List.of(),
                    List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("300")),
                            new PlannedOutput(2, RECEITA, TANQUE, new BigDecimal("295"))),
                    new BigDecimal("5"), "Separar para dry hopping distinto", ATOR, AGORA);

            assertThat(op.plannedOutputs()).hasSize(2);
        }

        @Test
        @DisplayName("O LOTE SÓ SE LIGA DEPOIS DA EXECUÇÃO")
        void naoLigaAntesDeExecutar() {
            // Um lote de resultado antes da execução seria cerveja no tanque sem ninguém ter aberto
            // válvula — e apareceria nas telas de produção como lote existente.
            var op = uniaoParaLoteNovo();

            assertThatIllegalStateException()
                    .isThrownBy(() -> op.linkResultBatch(1, UUID.randomUUID()));

            op.approve(ATOR, AGORA);
            assertThatIllegalStateException()
                    .isThrownBy(() -> op.linkResultBatch(1, UUID.randomUUID()));
        }

        @Test
        @DisplayName("A MESMA SAÍDA NÃO GERA DOIS LOTES")
        void naoLigaDuasVezes() {
            // Uma execução repetida criaria um segundo lote com o mesmo volume, dobrando cerveja que
            // nunca existiu. A recusa é do agregado, não da tela que chama.
            var op = uniaoParaLoteNovo();
            op.approve(ATOR, AGORA);
            op.execute(ATOR, AGORA);
            op.linkResultBatch(1, UUID.randomUUID());

            assertThatIllegalStateException()
                    .isThrownBy(() -> op.linkResultBatch(1, UUID.randomUUID()));
        }

        @Test
        @DisplayName("posição sem saída planejada é recusada")
        void posicaoInexistente() {
            var op = uniaoParaLoteNovo();
            op.approve(ATOR, AGORA);
            op.execute(ATOR, AGORA);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> op.linkResultBatch(2, UUID.randomUUID()));
        }

        @Test
        @DisplayName("posição repetida no plano é recusada")
        void posicaoRepetida() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.SPLIT,
                            List.of(mov("600")), List.of(),
                            List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("300")),
                                    new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("300"))),
                            BigDecimal.ZERO, "x", ATOR, AGORA));
        }

        @Test
        @DisplayName("PARA A GENEALOGIA, LOTE CRIADO É DESTINO COMO OUTRO QUALQUER")
        void destinoIncluiOCriado() {
            // Quem investiga um recall não distingue lote de resultado de lote de destino pré-existente:
            // a diferença é de origem, não de consequência. Se o criado ficasse de fora, o recall pararia
            // exatamente no lote que a união produziu.
            var existente = mov("300");
            var op = BlendOperation.simulate(UUID.randomUUID(), CERVEJARIA, BlendKind.MERGE,
                    List.of(mov("400"), mov("200")), List.of(existente),
                    List.of(new PlannedOutput(1, RECEITA, TANQUE, new BigDecimal("288"))),
                    new BigDecimal("12"), "x", ATOR, AGORA);
            op.approve(ATOR, AGORA);
            op.execute(ATOR, AGORA);
            var criado = UUID.randomUUID();
            op.linkResultBatch(1, criado);

            assertThat(op.destinationBatchIds()).containsExactlyInAnyOrder(existente.batchId(), criado);
        }

        @Test
        @DisplayName("saída planejada sem receita ou com volume zero é recusada")
        void planejadaInvalida() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new PlannedOutput(1, null, TANQUE, BigDecimal.TEN));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PlannedOutput(1, RECEITA, TANQUE, BigDecimal.ZERO));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PlannedOutput(0, RECEITA, TANQUE, BigDecimal.TEN));
        }
    }
}
