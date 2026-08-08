package br.com.brew.brassia.knowledge.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Divide o texto de um documento nos trechos que serão recuperados (RAG-001).
 *
 * <p><strong>Por que dividir.</strong> O trecho é a unidade de evidência: é ele que vai ser citado ao
 * lado de uma resposta, e é sobre ele que alguém vai conferir se a resposta está certa. Um manual
 * inteiro como um bloco só não é evidência de nada — ninguém confere quarenta páginas —, e um trecho
 * de uma linha é uma frase fora de contexto, que é como se cria uma afirmação que o documento não faz.
 *
 * <p><strong>Onde cortar, em ordem de preferência.</strong> Parágrafo, depois frase, depois palavra.
 * A ordem existe porque um corte no meio de uma frase estraga as duas metades: nenhuma das duas diz o
 * que o documento dizia. Cortar no fim de um parágrafo custa alguns caracteres de folga e preserva a
 * unidade de sentido — em documento técnico, o parágrafo geralmente é a regra inteira.
 *
 * <p><strong>Por que existe sobreposição.</strong> A informação que mais importa num documento técnico
 * é justamente a que atravessa fronteira: "a concentração é de 0,15%" numa linha e "por 20 minutos a
 * 45 °C" na seguinte. Cortadas em trechos diferentes, nenhum dos dois responde à pergunta inteira, e a
 * resposta sai pela metade com aparência de completa. A sobreposição repete o fim do trecho anterior
 * no começo do seguinte para que a passagem exista inteira em algum lugar.
 *
 * <p>Trecho guardado pode passar de {@code maxChars} pelo tamanho da sobreposição: o limite governa
 * quanto de <em>texto novo</em> cada trecho carrega, não o tamanho final.
 */
public final class Chunker {

    /**
     * Padrões de tamanho.
     *
     * <p>1500 caracteres é da ordem de um parágrafo técnico longo ou dois curtos — grande o bastante
     * para uma regra caber inteira, pequeno o bastante para caber vários trechos num prompt sem
     * estourar custo. 200 de sobreposição cobre uma frase técnica típica com folga.
     */
    public static final int DEFAULT_MAX_CHARS = 1500;
    public static final int DEFAULT_OVERLAP_CHARS = 200;

    /**
     * Piso do corte de frase, como fração do limite.
     *
     * <p>Serve só contra o caso degenerado: um "Rev. " ou "Sr. " no começo do parágrafo daria um fim de
     * frase na posição 5, e cortar ali produziria trechos de cinco caracteres sem avançar no texto. É um
     * piso contra abreviação, não um alvo de empacotamento — abaixo dele vale mais cortar em palavra
     * perto do limite do que picar o parágrafo.
     *
     * <p>Precisa ser relativo ao limite: um piso absoluto rejeitaria cortes de frase perfeitamente bons
     * quando o limite é pequeno, que foi o defeito que o teste {@code cortaEmFimDeFrase} pegou.
     */
    private static final int SENTENCE_CUT_FLOOR_DIVISOR = 4;

    private final int maxChars;
    private final int overlapChars;

    public Chunker(int maxChars, int overlapChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars deve ser positivo");
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("a sobreposição deve ser menor que maxChars");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public static Chunker standard() {
        return new Chunker(DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS);
    }

    /**
     * @return trechos na ordem do documento, nenhum vazio; lista vazia se o texto não tem conteúdo
     */
    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var pieces = new ArrayList<String>();
        for (var paragraph : paragraphsOf(text)) {
            pieces.addAll(fitToLimit(paragraph));
        }
        return packWithOverlap(pieces);
    }

    /** Parágrafos: linha em branco separa. É a fronteira mais forte que um texto técnico oferece. */
    private static List<String> paragraphsOf(String text) {
        var normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        var paragraphs = new ArrayList<String>();
        for (var block : normalized.split("\n[ \t]*\n+")) {
            var trimmed = block.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /** Quebra um parágrafo grande demais: primeiro por frase, e só então por palavra. */
    private List<String> fitToLimit(String paragraph) {
        if (paragraph.length() <= maxChars) {
            return List.of(paragraph);
        }
        var parts = new ArrayList<String>();
        var rest = paragraph;
        while (rest.length() > maxChars) {
            var cut = sentenceCut(rest);
            if (cut < maxChars / SENTENCE_CUT_FLOOR_DIVISOR) {
                cut = wordCut(rest);
            }
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    /**
     * Último fim de frase dentro do limite, ou 0 se não houver.
     *
     * <p>Reconhece o ponto seguido de espaço, e não o ponto sozinho, porque documento técnico é cheio
     * de números com ponto — "0.15", "pH 4.2", "Rev. 3" — e cortar ali partiria o número em dois.
     */
    private int sentenceCut(String text) {
        var window = text.substring(0, Math.min(maxChars, text.length()));
        var best = 0;
        for (var i = 0; i < window.length() - 1; i++) {
            if (isSentenceEnd(window.charAt(i)) && Character.isWhitespace(window.charAt(i + 1))) {
                best = i + 1;
            }
        }
        return best;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    /** Último espaço dentro do limite; sem nenhum, corta no limite — palavra de 1500 letras não existe. */
    private int wordCut(String text) {
        var window = text.substring(0, Math.min(maxChars, text.length()));
        var space = window.lastIndexOf(' ');
        return space > 0 ? space : window.length();
    }

    /**
     * Junta os pedaços em trechos e prefixa cada um com o fim do anterior.
     *
     * <p>Agrupar antes de sobrepor importa: sobrepor pedaço por pedaço repetiria o mesmo texto em
     * todos os trechos de um documento com parágrafos curtos, inflando o índice sem acrescentar nada.
     */
    private List<String> packWithOverlap(List<String> pieces) {
        var chunks = new ArrayList<String>();
        var current = new StringBuilder();
        for (var piece : pieces) {
            if (!current.isEmpty() && current.length() + piece.length() + 1 > maxChars) {
                chunks.add(withOverlap(chunks, current.toString()));
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(piece);
        }
        if (!current.isEmpty()) {
            chunks.add(withOverlap(chunks, current.toString()));
        }
        return List.copyOf(chunks);
    }

    private String withOverlap(List<String> previous, String content) {
        if (overlapChars == 0 || previous.isEmpty()) {
            return content;
        }
        var tail = tailOf(previous.getLast());
        return tail.isEmpty() ? content : tail + "\n" + content;
    }

    /** Fim do trecho anterior, cortado em palavra inteira para não começar o seguinte no meio de uma. */
    private String tailOf(String text) {
        if (text.length() <= overlapChars) {
            return text;
        }
        var tail = text.substring(text.length() - overlapChars);
        var space = tail.indexOf(' ');
        return space < 0 ? tail : tail.substring(space + 1).strip();
    }

    /**
     * Termos de busca a partir da pergunta do usuário.
     *
     * <p>Fica aqui, junto do corte, porque indexação e consulta precisam concordar sobre o que é uma
     * palavra: quem separa o texto e quem separa a pergunta não podem discordar, senão o índice guarda
     * uma coisa e a busca procura outra.
     *
     * <p><strong>Não remove palavra de parada, de propósito.</strong> Quem sabe que "que", "para" e "com"
     * não discriminam nada em português é o dicionário do PostgreSQL, e ele é a autoridade porque é o
     * mesmo que indexou o texto. Reproduzir essa lista aqui criaria uma segunda opinião sobre o assunto,
     * que divergiria da primeira no dia em que o dicionário mudar. O corte por tamanho existe só para
     * jogar fora o que nem palavra é.
     */
    public static List<String> termsOf(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        var terms = new ArrayList<String>();
        for (var raw : question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            // Uma e duas letras não discriminam nada em português — "de", "a", "o" casariam com tudo.
            if (raw.length() > 2 && !terms.contains(raw)) {
                terms.add(raw);
            }
        }
        return List.copyOf(terms);
    }
}
