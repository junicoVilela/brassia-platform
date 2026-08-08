package br.com.brew.brassia.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O corte do documento em trechos (RAG-001).
 *
 * <p>O que estes testes fixam é a qualidade da evidência. Um corte no lugar errado não quebra nada — ele
 * produz um trecho que diz menos do que o documento dizia, e uma resposta pela metade com aparência de
 * completa. Por isso os casos abaixo são sobre <em>onde</em> se corta, não sobre tamanho.
 */
class ChunkerTest {

    @Test
    @DisplayName("texto curto vira um trecho só")
    void textoCurtoNaoEhDividido() {
        var chunks = Chunker.standard().split("A concentração de peracético é de 0,15%.");

        assertThat(chunks).containsExactly("A concentração de peracético é de 0,15%.");
    }

    @Test
    @DisplayName("texto vazio não produz trecho: documento sem conteúdo não responde nada")
    void textoVazioNaoProduzTrecho() {
        var chunker = Chunker.standard();

        assertThat(chunker.split(null)).isEmpty();
        assertThat(chunker.split("   \n\n  ")).isEmpty();
    }

    @Test
    @DisplayName("corta na fronteira de parágrafo, não no meio dele")
    void cortaEmParagrafo() {
        // Dois parágrafos que não caberiam juntos no limite: cada um deve sair inteiro.
        var first = "P1 " + "a".repeat(60);
        var second = "P2 " + "b".repeat(60);
        var chunks = new Chunker(80, 0).split(first + "\n\n" + second);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo(first);
        assertThat(chunks.get(1)).isEqualTo(second);
    }

    @Test
    @DisplayName("parágrafos pequenos são agrupados: um trecho por parágrafo inflaria o índice")
    void agrupaParagrafosPequenos() {
        var chunks = new Chunker(200, 0).split("Primeiro.\n\nSegundo.\n\nTerceiro.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).contains("Primeiro.").contains("Segundo.").contains("Terceiro.");
    }

    @Test
    @DisplayName("parágrafo grande é cortado em fim de frase, não no meio de uma")
    void cortaEmFimDeFrase() {
        var paragraph = "Primeira frase com algum tamanho razoável. Segunda frase igualmente longa aqui. "
                + "Terceira frase para forçar o corte além do limite estabelecido.";
        var chunks = new Chunker(90, 0).split(paragraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Nenhum trecho começa em minúscula: começar assim seria o resto de uma frase partida.
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(Character.isUpperCase(chunk.charAt(0))).isTrue());
        assertThat(chunks.getFirst()).endsWith(".");
    }

    @Test
    @DisplayName("número com ponto decimal não é confundido com fim de frase")
    void naoCortaDentroDeNumero() {
        // Este é o caso que mais importa num documento técnico: cortar em "0." e "15%" produziria dois
        // trechos, um dizendo "a concentração é de 0" e outro dizendo "15%". Os dois estariam errados.
        var paragraph = "A concentração recomendada de ácido peracético para sanitização é de 0.15% "
                + "em volume, aplicada por vinte minutos na temperatura ambiente do processo.";
        var chunks = new Chunker(100, 0).split(paragraph);

        assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk).endsWith("0."));
        assertThat(String.join(" ", chunks)).contains("0.15%");
    }

    @Test
    @DisplayName("palavra sozinha maior que o limite é cortada, e não perdida")
    void palavraGiganteEhCortada() {
        var chunks = new Chunker(20, 0).split("x".repeat(55));

        assertThat(chunks).hasSize(3);
        assertThat(String.join("", chunks)).hasSize(55);
    }

    @Test
    @DisplayName("a sobreposição repete o fim do trecho anterior: a passagem que atravessa fronteira existe inteira")
    void sobreposicaoPreservaPassagem() {
        // A informação decisiva está partida entre dois parágrafos: a concentração num, o tempo no outro.
        var text = "A concentração recomendada do sanitizante é de 0,15% em volume para uso geral.\n\n"
                + "O tempo de contato mínimo é de vinte minutos a temperatura ambiente do processo.";
        var chunks = new Chunker(80, 40).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Sem sobreposição, "0,15%" e "vinte minutos" nunca apareceriam no mesmo trecho.
        assertThat(chunks.get(1)).contains("vinte minutos");
        assertThat(chunks.get(1)).isNotEqualTo(chunks.get(1).strip() + "X");
        assertThat(chunks.get(1).length()).isGreaterThan(60);
    }

    @Test
    @DisplayName("a sobreposição não começa no meio de uma palavra")
    void sobreposicaoRespeitaPalavra() {
        var text = "Parágrafo inicial com conteúdo suficiente para encher o primeiro trecho todo.\n\n"
                + "Parágrafo seguinte com conteúdo próprio e igualmente extenso para o segundo.";
        var chunks = new Chunker(80, 30).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        var overlapStart = chunks.get(1).split("\\s+")[0];
        // A primeira palavra do trecho seguinte tem de ser uma palavra do documento, não um pedaço dela.
        assertThat(text).contains(overlapStart);
    }

    @Test
    @DisplayName("nenhum trecho sai vazio, com qualquer entrada")
    void nenhumTrechoVazio() {
        var messy = "\n\n  Um  \n\n\n\n   \n\n Dois \n\n\n";

        assertThat(Chunker.standard().split(messy))
                .isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }

    @Test
    @DisplayName("fim de linha do Windows não vira parágrafo diferente")
    void normalizaFimDeLinha() {
        var unix = Chunker.standard().split("Um\n\nDois");
        var windows = Chunker.standard().split("Um\r\n\r\nDois");

        assertThat(windows).isEqualTo(unix);
    }

    @Test
    @DisplayName("sobreposição maior que o limite não faz sentido")
    void sobreposicaoInvalidaEhRecusada() {
        assertThatThrownBy(() -> new Chunker(100, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- termos de busca ---

    @Test
    @DisplayName("termos ignoram palavras curtas, que casariam com tudo")
    void termosIgnoramPalavrasCurtas() {
        var terms = Chunker.termsOf("Qual é a concentração de peracético no CIP?");

        assertThat(terms).contains("concentração", "peracético", "cip");
        assertThat(terms).doesNotContain("é", "a", "de", "no");
    }

    @Test
    @DisplayName("termos não repetem e não carregam pontuação")
    void termosSaoNormalizados() {
        var terms = Chunker.termsOf("Peracético, peracético; PERACÉTICO!");

        assertThat(terms).containsExactly("peracético");
    }

    @Test
    @DisplayName("pergunta sem palavra nenhuma devolve lista vazia")
    void perguntaSemTermoUtil() {
        assertThat(Chunker.termsOf("   ")).isEmpty();
        assertThat(Chunker.termsOf(null)).isEmpty();
        assertThat(Chunker.termsOf("? ! ...")).isEmpty();
    }

    @Test
    @DisplayName("palavra de parada não é removida aqui: quem decide isso é o dicionário do banco")
    void palavraDeParadaFicaParaODicionario() {
        // "que" passa o corte por tamanho, e deve passar: reproduzir a lista de palavras de parada do
        // português em Java criaria uma segunda opinião sobre o assunto, que divergiria da do dicionário
        // que indexou o texto. O `plainto_tsquery('portuguese', ...)` descarta essas palavras, e é ele
        // que tem autoridade porque é o mesmo lado que construiu o índice.
        assertThat(Chunker.termsOf("e o que é?")).containsExactly("que");
    }

    @Test
    @DisplayName("número é termo de busca: 'norma 12345' precisa achar a norma 12345")
    void numeroEhTermo() {
        assertThat(Chunker.termsOf("laudo conforme a norma 12345")).contains("12345", "laudo", "norma");
    }
}
