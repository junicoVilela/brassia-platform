package br.com.brew.brassia.ai.domain;

import br.com.brew.brassia.knowledge.KnowledgeRetrieval.Evidence;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Confere se as citações que o modelo diz ter feito existem de verdade nas fontes recuperadas (RAG-002).
 *
 * <p><strong>Este é o freio contra alucinação deste módulo.</strong> Validar o formato da resposta, como o
 * gateway já faz, garante que ela tem os campos certos — não que o que está neles é verdade. Um modelo
 * pode devolver um JSON impecável citando um manual que não existe, ou atribuindo a um manual real uma
 * frase que ele nunca disse. As duas coisas são invisíveis para um validador de schema e as duas são
 * exatamente o erro que destrói a confiança numa resposta com fonte.
 *
 * <p>A conferência é possível porque o sistema sabe o que entregou ao modelo: a citação é comparada contra
 * os mesmos trechos que foram ao prompt. Se o trecho citado não estava lá, a citação é inventada. Se a
 * frase citada não está no trecho, a frase é inventada. Nos dois casos ela é descartada, e o motivo é
 * registrado — porque uma citação rejeitada é o sinal mais útil que existe de que o prompt precisa mudar.
 *
 * <p><strong>Por que o domínio de IA conhece um tipo do módulo de conhecimento.</strong> {@code Evidence} é
 * a consulta publicada do {@code knowledge} — comunicação entre módulos permitida, e o
 * {@code ModularityTest} a valida. Criar aqui um record idêntico só para traduzir isolaria o domínio de uma
 * mudança de contrato, mas duplicaria o tipo sem isolar risco, variação nem teste: {@code Evidence} é
 * exatamente o valor sobre o qual esta regra opera, e é a fronteira estável que o outro módulo publica.
 *
 * <p><strong>Não é comparação literal.</strong> O modelo reescreve espaços, quebra de linha e maiúsculas ao
 * copiar, e recusar por causa disso rejeitaria citação honesta. O que se compara é o texto normalizado —
 * espaço colapsado, caixa e acento dobrados. O que <em>não</em> se tolera é palavra diferente: a
 * normalização não aproxima sentido, só forma.
 */
public final class Grounding {

    /**
     * Tamanho mínimo de uma citação, em caracteres normalizados.
     *
     * <p>Abaixo disto a verificação perde o sentido: "de 0,15%" aparece em quase qualquer documento
     * técnico, e conferir que ele está no trecho não prova que o trecho sustenta a afirmação. Citação curta
     * demais não é evidência, é coincidência.
     */
    private static final int MIN_QUOTE_LENGTH = 20;

    private Grounding() {
    }

    /**
     * Uma citação como o modelo a declarou — ainda não conferida.
     *
     * @param documentCode código do documento que o modelo diz ter citado
     * @param ordinal      trecho que o modelo diz ter citado
     * @param quote        a frase que o modelo diz estar naquele trecho
     */
    public record ClaimedCitation(String documentCode, int ordinal, String quote) {

        public ClaimedCitation {
            if (documentCode == null || documentCode.isBlank()) {
                throw new IllegalArgumentException("citação sem documento não é citação");
            }
            if (quote == null || quote.isBlank()) {
                throw new IllegalArgumentException("citação sem trecho citado não é citação");
            }
            documentCode = documentCode.strip();
            quote = quote.strip();
        }
    }

    /**
     * Uma citação conferida: existe, e a frase está mesmo no trecho.
     *
     * <p>Carrega os metadados do documento — título, tipo, versão, vigência — porque eles vêm da fonte, e
     * não da resposta do modelo. Deixar o modelo informar o título do que citou seria dar a ele a
     * oportunidade de errar sobre um dado que o sistema já sabe.
     */
    public record VerifiedCitation(
            String documentCode,
            String title,
            String type,
            int version,
            boolean effectiveOnDate,
            int ordinal,
            String quote) {
    }

    /**
     * O resultado da conferência.
     *
     * @param verified as citações que existem e conferem
     * @param rejected o motivo de cada citação descartada, por extenso, sem o conteúdo rejeitado
     */
    public record Verification(List<VerifiedCitation> verified, List<String> rejected) {

        public Verification {
            verified = List.copyOf(verified);
            rejected = List.copyOf(rejected);
        }

        public boolean anyVerified() {
            return !verified.isEmpty();
        }
    }

    /**
     * Confere cada citação declarada contra as evidências que foram ao prompt.
     *
     * @param evidence o que o sistema entregou ao modelo — a única base legítima de citação
     * @param claimed  o que o modelo diz ter citado
     */
    public static Verification verify(List<Evidence> evidence, List<ClaimedCitation> claimed) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(claimed, "claimed");

        var verified = new ArrayList<VerifiedCitation>();
        var rejected = new ArrayList<String>();

        for (var citation : claimed) {
            var source = sourceOf(evidence, citation);
            if (source == null) {
                // O modelo citou algo que não estava no prompt. Não é engano de transcrição: é fonte
                // inventada, e é o caso mais grave porque parece verificável.
                rejected.add("o trecho %d de %s não estava entre as fontes consultadas"
                        .formatted(citation.ordinal(), citation.documentCode()));
                continue;
            }
            var quote = normalize(citation.quote());
            if (quote.length() < MIN_QUOTE_LENGTH) {
                rejected.add("a citação de %s é curta demais para ser conferida"
                        .formatted(citation.documentCode()));
                continue;
            }
            if (!normalize(source.text()).contains(quote)) {
                // A fonte existe, a frase não. É a alucinação mais convincente que existe: documento real,
                // afirmação inventada.
                rejected.add("a frase atribuída a %s não está no trecho %d"
                        .formatted(citation.documentCode(), citation.ordinal()));
                continue;
            }
            verified.add(new VerifiedCitation(source.code(), source.title(), source.type(),
                    source.version(), source.effectiveOn(), source.ordinal(), citation.quote()));
        }
        return new Verification(verified, rejected);
    }

    private static Evidence sourceOf(List<Evidence> evidence, ClaimedCitation citation) {
        return evidence.stream()
                .filter(e -> e.code().equalsIgnoreCase(citation.documentCode())
                        && e.ordinal() == citation.ordinal())
                .findFirst()
                .orElse(null);
    }

    /**
     * Dobra o que é forma e preserva o que é palavra.
     *
     * <p>Espaço colapsado porque o modelo reflui o texto; caixa e acento dobrados porque ele os reescreve.
     * Nada além disso: aproximar sentido aqui — sinônimo, radical — faria a conferência aceitar uma
     * paráfrase como se fosse citação, que é justamente o que ela existe para pegar.
     */
    private static String normalize(String text) {
        var collapsed = text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(collapsed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
