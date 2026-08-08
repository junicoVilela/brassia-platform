package br.com.brew.brassia.ai.adapter.outbound.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.application.port.inbound.GatewayCommands.ProbeAnswer;
import br.com.brew.brassia.ai.domain.InvalidModelResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A leitura desconfiada da resposta do modelo (AIA-001).
 *
 * <p>Este é o teste de alucinação do gateway. Cada caso abaixo é uma forma de o modelo entregar algo
 * que <em>parece</em> resposta e não é — e em todas o sistema recusa inteiro, em vez de aproveitar a
 * parte que veio certa e inventar o resto.
 */
class JacksonStructuredResponseReaderTest {

    private final JacksonStructuredResponseReader reader = new JacksonStructuredResponseReader();

    @Test
    @DisplayName("resposta na forma pedida é aceita")
    void respostaValidaEhAceita() {
        var answer = reader.read("{\"ready\": true, \"note\": \"consigo responder em JSON\"}",
                ProbeAnswer.class);

        assertThat(answer.ready()).isTrue();
        assertThat(answer.note()).isEqualTo("consigo responder em JSON");
    }

    @Test
    @DisplayName("campo booleano ausente é recusa, não falso")
    void booleanAusenteNaoViraFalso() {
        // O caso mais perigoso da lista: sem FAIL_ON_NULL_FOR_PRIMITIVES um `ready` ausente viraria
        // `false`, e "o modelo não respondeu" passaria por "o modelo respondeu que não está pronto".
        assertThatThrownBy(() -> reader.read("{\"note\": \"oi\"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("campo inventado pelo modelo é recusa: a estrutura não é a que pedimos")
    void campoDesconhecidoEhRecusado() {
        assertThatThrownBy(() -> reader.read(
                "{\"ready\": true, \"note\": \"oi\", \"confidence\": 0.9}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("invariante do próprio contrato vale na desserialização")
    void invarianteDoContratoVale() {
        // `note` vazio passa pelo tipo e falha na regra. O construtor do contrato é a última palavra.
        assertThatThrownBy(() -> reader.read("{\"ready\": true, \"note\": \"  \"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
        assertThatThrownBy(() -> reader.read(
                "{\"ready\": true, \"note\": \"" + "x".repeat(201) + "\"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("texto em volta do JSON é recusa: o modelo não respeitou o formato")
    void textoAoRedorEhRecusado() {
        // O modelo explicando o que fez antes do JSON, ou cercando em blocos de markdown, é o desvio mais
        // comum. Tolerar aqui seria começar a adivinhar onde a resposta começa.
        assertThatThrownBy(() -> reader.read(
                "Claro! Aqui está:\n{\"ready\": true, \"note\": \"oi\"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
        assertThatThrownBy(() -> reader.read(
                "```json\n{\"ready\": true, \"note\": \"oi\"}\n```", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("tipo errado no campo é recusa")
    void tipoErradoEhRecusado() {
        assertThatThrownBy(() -> reader.read("{\"ready\": \"sim\", \"note\": \"oi\"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("resposta vazia é recusa")
    void vazioEhRecusado() {
        assertThatThrownBy(() -> reader.read("", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
        assertThatThrownBy(() -> reader.read(null, ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class);
    }

    @Test
    @DisplayName("a recusa não repete o conteúdo recusado")
    void recusaNaoVazaConteudo() {
        // A resposta do modelo carrega o que estava no prompt, e prompt carrega POP, laudo e medição. A
        // mensagem que chega ao usuário diz o que falhou, nunca o que estava escrito.
        var secret = "concentração de peracético 0,15% no tanque T-3";

        assertThatThrownBy(() -> reader.read(
                "{\"ready\": true, \"note\": \"oi\", \"leak\": \"" + secret + "\"}", ProbeAnswer.class))
                .isInstanceOf(InvalidModelResponseException.class)
                .hasMessageNotContaining(secret)
                .hasMessageContaining("ProbeAnswer");
    }
}
