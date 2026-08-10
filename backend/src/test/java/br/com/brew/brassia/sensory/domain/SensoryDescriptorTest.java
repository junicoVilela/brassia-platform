package br.com.brew.brassia.sensory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Biblioteca de descritores (SEN-002).
 *
 * <p>O que estes testes fixam: o limiar não existe sem licença que o autorize, causa é hipótese com
 * verificação, e o vocabulário encontra o termo como ele é escrito na mesa de prova.
 */
class SensoryDescriptorTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();

    private static SensoryDescriptor descritor(DescriptorSource fonte, BigDecimal limiar, String unidade) {
        return SensoryDescriptor.create(UUID.randomUUID(), CERVEJARIA, "papelao", "Papelão",
                DescriptorCategory.OFF_FLAVOR, Set.of("cartonado", "molhado"), fonte, limiar, unidade,
                List.of(new Hypothesis("Oxidação por absorção de O₂ no envase",
                        "Conferir o oxigênio dissolvido no envase do lote",
                        Hypothesis.Likelihood.COMMON)));
    }

    @Nested
    @DisplayName("licença e limiar")
    class Licenca {

        @Test
        @DisplayName("O LIMIAR NÃO É GRAVADO quando a licença não o autoriza")
        void limiarSemLicencaRecusado() {
            // Recusa na criação, e não filtro na leitura: um dado que não pode ser publicado e mesmo
            // assim está gravado é um vazamento esperando exportação.
            var restrita = new DescriptorSource("Catálogo X", "ref", LicenseTier.LICENSED_INTERNAL_ONLY,
                    "© Catálogo X");

            assertThatExceptionOfType(SensoryDescriptor.ThresholdNotLicensedException.class)
                    .isThrownBy(() -> descritor(restrita, new BigDecimal("0.1"), "ug/L"));
        }

        @Test
        @DisplayName("fonte própria autoriza limiar")
        void fontePropriaAutoriza() {
            var d = descritor(DescriptorSource.own("Painel interno"), new BigDecimal("0.1"), "ug/L");

            assertThat(d.perceptionThreshold()).contains(new BigDecimal("0.1"));
            assertThat(d.exportable()).isTrue();
        }

        @Test
        @DisplayName("licença com atribuição EXIGE o texto de atribuição")
        void atribuicaoObrigatoria() {
            // Deixar opcional transformaria a regra da licença em lembrete — e lembrete é o que se
            // esquece na exportação.
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new DescriptorSource("Fonte pública", "ref", LicenseTier.ATTRIBUTION_REQUIRED, null));
        }

        @Test
        @DisplayName("conteúdo de uso interno não é exportável")
        void internoNaoExporta() {
            var restrita = new DescriptorSource("Catálogo X", "ref", LicenseTier.LICENSED_INTERNAL_ONLY,
                    "© Catálogo X");

            assertThat(descritor(restrita, null, null).exportable()).isFalse();
        }

        @Test
        @DisplayName("limiar sem unidade é recusado")
        void limiarSemUnidade() {
            // 0,1 pode ser µg/L, mg/L ou ppm — a diferença entre eles é de mil vezes.
            assertThatIllegalArgumentException().isThrownBy(() ->
                    descritor(DescriptorSource.own("Painel"), new BigDecimal("0.1"), null));
        }
    }

    @Nested
    @DisplayName("causa é hipótese, não diagnóstico")
    class Hipotese {

        @Test
        @DisplayName("A HIPÓTESE EXIGE COMO VERIFICAR")
        void exigeVerificacao() {
            // "Pode ser infecção" sem dizer como confirmar deixa quem lê com a preocupação e sem o
            // próximo passo.
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new Hypothesis("Infecção por Lactobacillus", "  ", Hypothesis.Likelihood.OCCASIONAL));
        }

        @Test
        @DisplayName("o tipo se chama Hypothesis, e o nome é a garantia")
        void nomeDoTipoCarregaOLimite() throws Exception {
            // Chamar isto de Diagnosis faria o mesmo dado significar outra coisa para quem lê o código e,
            // depois, para quem lê a tela. Mesma decisão de Estimate (DTW-001) e supported (OPT-001).
            var campo = SensoryDescriptor.class.getDeclaredField("hypotheses");

            assertThat(campo.getGenericType().getTypeName()).contains("Hypothesis");
            assertThat(SensoryDescriptor.class.getDeclaredFields())
                    .noneMatch(f -> f.getName().toLowerCase().contains("diagnos"));
        }

        @Test
        @DisplayName("a probabilidade é qualitativa, não número")
        void probabilidadeQualitativa() {
            // Um número daria falsa precisão a algo que ninguém mediu nesta cervejaria.
            for (var l : Hypothesis.Likelihood.values()) {
                assertThat(l.name()).isNotBlank();
            }
            assertThat(Hypothesis.Likelihood.values()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("vocabulário")
    class Vocabulario {

        private final SensoryDescriptor d = descritor(DescriptorSource.own("Painel"), null, null);

        @Test
        @DisplayName("ENCONTRA SINÔNIMO, sem acento e sem caixa")
        void encontraSinonimo() {
            // Quem anota na mesa de prova escreve "cartonado" e "Cartonádo". Um vocabulário que só
            // encontra o termo exato não serve para o momento em que é usado — com a taça na mão.
            assertThat(d.matches("cartonado")).isTrue();
            assertThat(d.matches("CARTONADO")).isTrue();
            assertThat(d.matches("Papelão")).isTrue();
            assertThat(d.matches("papelao")).isTrue();
            assertThat(d.matches("molhado")).isTrue();
        }

        @Test
        @DisplayName("não casa termo diferente")
        void naoCasaOutro() {
            assertThat(d.matches("diacetil")).isFalse();
            assertThat(d.matches("")).isFalse();
            assertThat(d.matches(null)).isFalse();
        }

        @Test
        @DisplayName("o código é normalizado em maiúsculas")
        void codigoNormalizado() {
            assertThat(d.code()).isEqualTo("PAPELAO");
        }
    }
}
