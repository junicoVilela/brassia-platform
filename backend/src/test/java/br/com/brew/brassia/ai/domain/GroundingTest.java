package br.com.brew.brassia.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.domain.Grounding.ClaimedCitation;
import br.com.brew.brassia.knowledge.KnowledgeRetrieval.Evidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A conferência de citações (RAG-002).
 *
 * <p>Este é o teste de alucinação da história. O gateway já garante que a resposta tem os campos certos;
 * aqui se garante que o que está neles é verdade. Os dois casos que nenhum validador de schema pega estão
 * nomeados abaixo: documento inventado e frase inventada em documento real.
 */
class GroundingTest {

    private static final String TEXT = """
            A concentração recomendada de ácido peracético é de 0,15% em volume.
            O tempo de contato mínimo é de vinte minutos na temperatura ambiente.
            """;

    @Test
    @DisplayName("citação que existe e confere é aceita, com os metadados vindos da fonte")
    void citacaoHonestaEhAceita() {
        // Os metadados do documento vêm da evidência, não da resposta do modelo: deixá-lo informar o
        // título do que citou seria dar a ele a chance de errar sobre um dado que o sistema já sabe.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "A concentração recomendada de ácido peracético é de 0,15% em volume.")));

        assertThat(verification.anyVerified()).isTrue();
        assertThat(verification.rejected()).isEmpty();
        assertThat(verification.verified()).singleElement().satisfies(citation -> {
            assertThat(citation.documentCode()).isEqualTo("FISPQ-PERAC");
            assertThat(citation.title()).isEqualTo("FISPQ — Ácido peracético");
            assertThat(citation.version()).isEqualTo(2);
            assertThat(citation.effectiveOnDate()).isTrue();
        });
    }

    @Test
    @DisplayName("documento que não estava entre as fontes é recusado: fonte inventada")
    void documentoInventadoEhRecusado() {
        // O caso mais grave, porque parece verificável: o modelo cita um manual que nunca foi ao prompt.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("MANUAL-QUE-NAO-EXISTE", 0,
                        "A concentração recomendada de ácido peracético é de 0,15% em volume.")));

        assertThat(verification.anyVerified()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("não estava entre as fontes"));
    }

    @Test
    @DisplayName("trecho de número diferente é recusado, mesmo com o documento certo")
    void trechoErradoEhRecusado() {
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 7,
                        "A concentração recomendada de ácido peracético é de 0,15% em volume.")));

        assertThat(verification.anyVerified()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("trecho 7"));
    }

    @Test
    @DisplayName("frase que não está no documento real é recusada: a alucinação mais convincente")
    void fraseInventadaEhRecusada() {
        // Documento real, número de trecho real, afirmação inventada. Sem esta conferência, esta resposta
        // chegaria ao usuário com fonte e tudo.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "A concentração recomendada de ácido peracético é de 0,80% em volume.")));

        assertThat(verification.anyVerified()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("não está no trecho"));
    }

    @Test
    @DisplayName("espaço, quebra de linha e caixa não invalidam citação honesta")
    void formaDiferenteNaoInvalida() {
        // O modelo reflui o texto ao copiar. Recusar por isso rejeitaria citação honesta a torto e a
        // direito, e ensinaria a ignorar a rejeição.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "  a CONCENTRAÇÃO   recomendada de ácido\n peracético é de 0,15% em volume. ")));

        assertThat(verification.anyVerified()).isTrue();
    }

    @Test
    @DisplayName("acento ausente não invalida: o modelo reescreve acentuação ao copiar")
    void acentoNaoInvalida() {
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "A concentracao recomendada de acido peracetico e de 0,15% em volume.")));

        assertThat(verification.anyVerified()).isTrue();
    }

    @Test
    @DisplayName("paráfrase não passa: a normalização dobra forma, não aproxima sentido")
    void parafraseNaoPassa() {
        // É a linha que separa "tolerar transcrição" de "aceitar invenção". Trocar palavra é inventar.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "A dosagem sugerida do sanitizante peracético é de 0,15% do volume total.")));

        assertThat(verification.anyVerified()).isFalse();
    }

    @Test
    @DisplayName("citação curta demais é recusada: coincidência não é evidência")
    void citacaoCurtaEhRecusada() {
        // "de 0,15%" está em quase todo documento técnico do assunto; conferir que está no trecho não prova
        // que o trecho sustenta a afirmação.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0, "de 0,15%")));

        assertThat(verification.anyVerified()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("curta demais"));
    }

    @Test
    @DisplayName("as boas passam e as ruins caem, na mesma resposta")
    void separaBoasDeRuins() {
        // O caso realista: o modelo acerta uma citação e inventa outra. A resposta não é descartada por
        // causa da segunda, mas a segunda não entra.
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                                "O tempo de contato mínimo é de vinte minutos na temperatura ambiente."),
                        new ClaimedCitation("FISPQ-PERAC", 0,
                                "O produto pode ser aplicado sem enxágue posterior em qualquer superfície.")));

        assertThat(verification.verified()).hasSize(1);
        assertThat(verification.rejected()).hasSize(1);
        assertThat(verification.anyVerified()).isTrue();
    }

    @Test
    @DisplayName("o motivo da recusa não repete o conteúdo recusado")
    void motivoNaoVazaConteudo() {
        // Prompt e resposta carregam o que estava no documento; o motivo diz o que falhou, não o que estava
        // escrito.
        var invented = "concentração de 0,80% no tanque T-3 do cliente Zé";
        var verification = Grounding.verify(List.of(evidence(0, TEXT)),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0, invented)));

        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).doesNotContain(invented));
    }

    @Test
    @DisplayName("citação de trecho de versão substituída é aceita, e marcada como tal")
    void versaoSubstituidaEhMarcada() {
        // Aceitar é certo — a pergunta pode ser sobre o passado. Marcar é obrigatório: "a ficha vigente diz"
        // e "uma ficha substituída dizia" não são a mesma afirmação.
        var superseded = new Evidence(UUID.randomUUID(), "FISPQ-PERAC", "FISPQ — Ácido peracético",
                "SAFETY_DATA_SHEET", 1, false, 0, TEXT, 0.5);

        var verification = Grounding.verify(List.of(superseded),
                List.of(new ClaimedCitation("FISPQ-PERAC", 0,
                        "A concentração recomendada de ácido peracético é de 0,15% em volume.")));

        assertThat(verification.verified()).singleElement()
                .satisfies(citation -> assertThat(citation.effectiveOnDate()).isFalse());
    }

    @Test
    @DisplayName("sem citação nenhuma, nada confere e nada é recusado")
    void semCitacaoNaoHaVerificacao() {
        var verification = Grounding.verify(List.of(evidence(0, TEXT)), List.of());

        assertThat(verification.anyVerified()).isFalse();
        assertThat(verification.rejected()).isEmpty();
    }

    @Test
    @DisplayName("citação sem documento ou sem frase não é citação")
    void citacaoIncompletaEhRecusada() {
        assertThatThrownBy(() -> new ClaimedCitation("  ", 0, "frase suficientemente longa aqui"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimedCitation("FISPQ", 0, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Evidence evidence(int ordinal, String text) {
        return new Evidence(UUID.randomUUID(), "FISPQ-PERAC", "FISPQ — Ácido peracético",
                "SAFETY_DATA_SHEET", 2, true, ordinal, text, 0.5);
    }
}
