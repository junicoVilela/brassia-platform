package br.com.brew.brassia.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.domain.FactGrounding.Claim;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A varredura de números (AIA-002).
 *
 * <p>É o critério "nenhum número é inventado" virado em teste. O que estes casos fixam é a diferença entre
 * interpretar um número e produzir um: a primeira é o trabalho do modelo, a segunda é do domínio, e uma
 * avaliação de lote não sobrevive à confusão entre as duas.
 */
class FactGroundingTest {

    private static final List<Fact> FACTS = List.of(
            Fact.of("volume_planejado", "Volume planejado", new BigDecimal("400"), "L", "production"),
            Fact.of("volume_transferido", "Volume transferido", new BigDecimal("390"), "L", "production"),
            Fact.of("perda_percentual", "Perda percentual", new BigDecimal("2.5"), "%",
                    "production (derivado)"),
            Fact.count("desvios", "Desvios", 3, "quality"),
            Fact.absent("medicoes", "Medições", "quality"));

    @Test
    @DisplayName("afirmação que só usa números de fato é aceita")
    void afirmacaoComNumerosDeFatoEhAceita() {
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "O lote transferiu 390 L dos 400 L planejados, com perda de 2.5%.",
                List.of("volume_transferido", "volume_planejado", "perda_percentual"))));

        assertThat(verification.anyAccepted()).isTrue();
        assertThat(verification.rejected()).isEmpty();
    }

    @Test
    @DisplayName("afirmação sem número nenhum é aceita: interpretar em palavras é o trabalho")
    void afirmacaoSemNumeroEhAceita() {
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "A perda na transferência está dentro do que se espera para o equipamento.",
                List.of("perda_percentual"))));

        assertThat(verification.anyAccepted()).isTrue();
    }

    @Test
    @DisplayName("número que não é fato derruba a afirmação")
    void numeroInventadoDerrubaAfirmacao() {
        // O caso central: 22% não foi calculado por ninguém, e quem lê não tem como saber disso.
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "A perda na transferência foi de 22%, acima do aceitável.",
                List.of("perda_percentual"))));

        assertThat(verification.anyAccepted()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("22")
                        .contains("não é o valor de nenhum fato que ela cita"));
    }

    @Test
    @DisplayName("aritmética derivada é recusada: conta feita pelo modelo não é fato")
    void aritmeticaDerivadaEhRecusada() {
        // 400 - 390 = 10 é aritmética correta e ainda assim inaceitável: o resultado não foi calculado por
        // nenhum serviço, e é por isso que o montador de fatos entrega derivados prontos.
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "A diferença entre planejado e transferido foi de 10 L.",
                List.of("volume_planejado", "volume_transferido"))));

        assertThat(verification.anyAccepted()).isFalse();
    }

    @Test
    @DisplayName("separador decimal diferente não invalida: 2,5 e 2.5 são o mesmo número")
    void separadorDecimalNaoInvalida() {
        // Recusar por causa da forma rejeitaria afirmação honesta, e ensinaria a ignorar a rejeição.
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("A perda foi de 2,5%.", List.of("perda_percentual"))));

        assertThat(verification.anyAccepted()).isTrue();
    }

    @Test
    @DisplayName("zero à direita não invalida: 390,0 é 390")
    void zeroADireitaNaoInvalida() {
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("Transferiu 390,0 L.", List.of("volume_transferido"))));

        assertThat(verification.anyAccepted()).isTrue();
    }

    @Test
    @DisplayName("ponto final depois do número não vira parte dele")
    void pontoFinalNaoInvalida() {
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("O volume transferido foi 390.", List.of("volume_transferido"))));

        assertThat(verification.anyAccepted()).isTrue();
    }

    @Test
    @DisplayName("referência a fato que não existe derruba a afirmação")
    void fatoInexistenteDerrubaAfirmacao() {
        // O modelo apontou para um cálculo que ninguém fez — sinal de que ele inventou a base, não só o
        // número.
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "A eficiência de mosturação ficou abaixo do previsto.", List.of("eficiencia_mostura"))));

        assertThat(verification.anyAccepted()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("eficiencia_mostura")
                        .contains("não foi calculado"));
    }

    @Test
    @DisplayName("fato ausente pode ser citado: a ausência é informação")
    void fatoAusentePodeSerCitado() {
        // "Ninguém mediu" é risco de desconhecimento, e o modelo precisa poder apontá-lo.
        var verification = FactGrounding.verify(FACTS, List.of(new Claim(
                "Não há medição de qualidade registrada, então não é possível afirmar conformidade.",
                List.of("medicoes"))));

        assertThat(verification.anyAccepted()).isTrue();
    }

    @Test
    @DisplayName("as boas passam e as ruins caem, na mesma avaliação")
    void separaBoasDeRuins() {
        // Descartar tudo por causa de uma frase jogaria fora análise boa; manter a suspeita ao lado das boas
        // deixaria a pior com a mesma aparência das melhores.
        var verification = FactGrounding.verify(FACTS, List.of(
                new Claim("Transferiu 390 L.", List.of("volume_transferido")),
                new Claim("A eficiência foi de 78%.", List.of("volume_planejado"))));

        assertThat(verification.accepted()).containsExactly("Transferiu 390 L.");
        assertThat(verification.rejected()).hasSize(1);
    }

    @Test
    @DisplayName("número certo citando o fato errado é recusado — o caso do IBU")
    void numeroCertoComFatoErradoEhRecusado() {
        // Este é o defeito que um teste pegou na primeira versão: "perdeu 45 L" passava porque 45 existia
        // entre os fatos — era o IBU previsto da receita. Número existente, assunto errado, afirmação errada
        // por 35 litros. Amarrar o número aos fatos que a afirmação cita fecha essa porta.
        var facts = List.of(
                Fact.of("perda_transferencia", "Perda", new BigDecimal("10"), "L", "production"),
                Fact.of("receita_ibu", "Amargor previsto", new BigDecimal("45"), "IBU", "recipe"));

        var verification = FactGrounding.verify(facts, List.of(
                new Claim("O lote perdeu 45 L na transferência.", List.of("perda_transferencia"))));

        assertThat(verification.anyAccepted()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("45"));
    }

    @Test
    @DisplayName("afirmação sem fato citado não pode conter número: não há contra o que conferir")
    void afirmacaoSemRefNaoPodeTerNumero() {
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("O lote transferiu 390 L.", List.of())));

        assertThat(verification.anyAccepted()).isFalse();
        assertThat(verification.rejected()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("sem citar de qual fato"));
    }

    @Test
    @DisplayName("token que não é número é tratado como inventado")
    void tokenNaoNumericoEhRecusado() {
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("Versão 12.3.4 do procedimento.", List.of())));

        assertThat(verification.anyAccepted()).isFalse();
    }

    @Test
    @DisplayName("afirmação vazia é ignorada, não recusada")
    void afirmacaoVaziaEhIgnorada() {
        var verification = FactGrounding.verify(FACTS,
                List.of(new Claim("   ", List.of()),
                        new Claim("Transferiu 390 L.", List.of("volume_transferido"))));

        assertThat(verification.accepted()).hasSize(1);
        assertThat(verification.rejected()).isEmpty();
    }

    @Test
    @DisplayName("identificador de fato não pode ter dígito: senão a varredura o leria como número")
    void identificadorNaoTemDigito() {
        assertThatThrownBy(() -> Fact.count("desvios2", "Desvios", 1, "quality"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apenas letras minúsculas");
    }

    @Test
    @DisplayName("fato ausente não vira zero")
    void fatoAusenteNaoEhZero() {
        // Um zero no lugar da ausência faria o modelo ler um lote não medido como um lote aprovado.
        var absent = Fact.absent("medicoes", "Medições", "quality");

        assertThat(absent.known()).isFalse();
        assertThat(absent.value()).isNull();
        // E o zero não passa a valer como número conhecido só porque existe um fato ausente.
        var verification = FactGrounding.verify(List.of(absent),
                List.of(new Claim("Foram 0 medições.", List.of("medicoes"))));
        assertThat(verification.anyAccepted()).isFalse();
    }
}
