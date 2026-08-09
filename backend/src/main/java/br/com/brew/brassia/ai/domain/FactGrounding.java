package br.com.brew.brassia.ai.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Confere que o modelo não inventou número nenhum (AIA-002).
 *
 * <p><strong>É o critério da história virado em código.</strong> "Nenhum número é inventado" não se garante
 * pedindo ao modelo que não invente: garante-se varrendo o texto dele e exigindo que cada número encontrado
 * corresponda a um valor que o domínio calculou. Um número solto numa avaliação de lote não é detalhe de
 * estilo — é a diferença entre "a perda foi de 12%" e "a perda foi de 22%", e ninguém que lê consegue
 * distinguir as duas sem ir conferir.
 *
 * <p><strong>Por que aritmética derivada é recusada.</strong> Se o modelo calcula uma porcentagem a partir de
 * dois fatos, o resultado não é fato — é conta feita por quem não presta contas dela. O caminho certo é o
 * domínio calcular o derivado e oferecê-lo como fato; é por isso que o montador de fatos já entrega perda
 * percentual e proporção fora da faixa prontas.
 *
 * <p><strong>Recusa é por afirmação, não pela avaliação inteira.</strong> Uma frase com número inventado sai,
 * e o motivo é reportado; as outras ficam. Descartar tudo por causa de uma frase jogaria fora análise boa, e
 * apresentar a frase suspeita junto das boas deixaria a pior com a mesma aparência das melhores.
 *
 * <p><strong>O número é conferido contra os fatos que a afirmação cita, não contra todos.</strong> Esta
 * distinção não é rigor gratuito: um teste pegou "o lote perdeu 45 L" passando porque 45 era o IBU previsto da
 * receita. O número existia entre os fatos, em outra unidade e sobre outro assunto — e a afirmação estava
 * errada por 35 litros. Amarrar cada número aos fatos que a própria afirmação declara usar fecha essa porta:
 * quem cita a perda percentual só pode escrever o valor da perda percentual.
 *
 * <p>Consequência direta: <strong>afirmação sem fato citado não pode conter número.</strong> Se ela não diz de
 * onde o número veio, não há contra o que conferir — e um número sem origem é exatamente o que esta classe
 * existe para impedir.
 */
public final class FactGrounding {

    /**
     * O que conta como número no texto.
     *
     * <p>Começa em dígito e admite ponto e vírgula internos, para pegar "0,15", "1.037" e "12%". Não pega
     * palavra com dígito no meio nem sublinhado — identificador de fato não tem dígito justamente para não
     * cair aqui.
     */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d.,]*");

    private FactGrounding() {
    }

    /**
     * Uma afirmação do modelo com os fatos que ela diz usar.
     *
     * @param text     o texto, que será varrido
     * @param factRefs identificadores de fato que a afirmação declara usar
     */
    public record Claim(String text, List<String> factRefs) {

        public Claim {
            text = text == null ? "" : text.strip();
            factRefs = List.copyOf(factRefs == null ? List.of() : factRefs);
        }
    }

    /**
     * O que sobrou e o que caiu.
     *
     * @param accepted afirmações cujos números e referências conferem
     * @param rejected motivo de cada afirmação descartada, sem repetir o conteúdo descartado
     */
    public record Verification(List<String> accepted, List<String> rejected) {

        public Verification {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
        }

        public boolean anyAccepted() {
            return !accepted.isEmpty();
        }
    }

    /**
     * Confere cada afirmação contra os fatos calculados.
     *
     * @param facts  os fatos que foram ao prompt — a única origem legítima de número
     * @param claims as afirmações do modelo
     */
    public static Verification verify(List<Fact> facts, List<Claim> claims) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(claims, "claims");

        var byId = new LinkedHashMap<String, Fact>();
        for (var fact : facts) {
            byId.put(fact.id(), fact);
        }

        var accepted = new ArrayList<String>();
        var rejected = new ArrayList<String>();

        for (var claim : claims) {
            if (claim.text().isEmpty()) {
                continue;
            }
            var unknownRef = firstUnknownRef(claim.factRefs(), byId.keySet());
            if (unknownRef != null) {
                // Referência a fato que não existe: o modelo apontou para um cálculo que ninguém fez.
                rejected.add("a afirmação cita o fato \"%s\", que não foi calculado".formatted(unknownRef));
                continue;
            }

            // Só os valores dos fatos que ESTA afirmação declara usar.
            var allowed = claim.factRefs().stream()
                    .map(byId::get)
                    .filter(Fact::known)
                    .map(Fact::value)
                    .toList();

            var invented = firstInventedNumber(claim.text(), allowed);
            if (invented != null) {
                rejected.add(claim.factRefs().isEmpty()
                        ? "a afirmação usa o número %s sem citar de qual fato ele vem".formatted(invented)
                        : "a afirmação usa o número %s, que não é o valor de nenhum fato que ela cita"
                                .formatted(invented));
                continue;
            }
            accepted.add(claim.text());
        }
        return new Verification(accepted, rejected);
    }

    private static String firstUnknownRef(List<String> refs, Set<String> knownIds) {
        return refs.stream().filter(ref -> !knownIds.contains(ref)).findFirst().orElse(null);
    }

    /**
     * O primeiro número do texto que não corresponde a fato, ou {@code null} se todos correspondem.
     *
     * <p>Comparação numérica, não textual: "0,15", "0.15" e "0.150" são o mesmo número, e recusar por causa
     * da forma rejeitaria afirmação honesta. Token que não se lê como número é tratado como inventado —
     * "12.3.4" não é número, e deixá-lo passar abriria a porta que a varredura existe para fechar.
     *
     * <p>{@code allowed} são os valores dos fatos que a afirmação cita. Lista vazia — afirmação sem fato
     * citado — significa que nenhum número é aceitável ali.
     */
    private static String firstInventedNumber(String text, List<BigDecimal> allowed) {
        var matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            var token = matcher.group();
            var parsed = parse(token);
            if (parsed == null) {
                return token;
            }
            var matches = allowed.stream().anyMatch(value -> value.compareTo(parsed) == 0);
            if (!matches) {
                return token;
            }
        }
        return null;
    }

    private static BigDecimal parse(String token) {
        // Vírgula é separador decimal em português; ponto é separador de milhar quando há vírgula, e
        // decimal quando não há. É a leitura que o texto de um brassador teria.
        var normalized = token.endsWith(".") || token.endsWith(",")
                ? token.substring(0, token.length() - 1)
                : token;
        normalized = normalized.contains(",")
                ? normalized.replace(".", "").replace(',', '.')
                : normalized;
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
