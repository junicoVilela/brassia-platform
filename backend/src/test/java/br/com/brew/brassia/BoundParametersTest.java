package br.com.brew.brassia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Todo parâmetro nomeado que aparece num SQL é ligado por um {@code .param(...)} (DEB-INT-003).
 *
 * <p><strong>Por que este teste existe.</strong> O {@code UPDATE} do outbox de webhooks filtrava por
 * {@code brewery_id = :brewery} e nunca ligava o parâmetro. Toda tentativa de entrega terminava em
 * exceção, e o efeito era o oposto do que o outbox promete: a entrega saía, o destino recebia, o desfecho
 * não era gravado, e o mesmo evento era despachado de novo a cada rodada — para sempre.
 *
 * <p><strong>Nenhum teste podia pegar isso, e o motivo é estrutural.</strong> O despachante tem teste de
 * unidade contra um repositório dublê, e o dublê não tem SQL. O IT do módulo cobria assinatura e
 * enfileiramento, mas não o {@code UPDATE}. Um parâmetro não ligado só aparece quando a instrução roda de
 * verdade, e a instrução que ninguém roda é justamente onde ele se esconde.
 *
 * <p>Corrigir a linha foi metade do trabalho. Esta é a outra metade, e segue o padrão que a
 * {@code TenantIsolationTest} estabeleceu: transformar a checagem que se faz uma vez, na mão, em barreira
 * de cada build. Custa uma varredura de texto e vale para os repositórios que ainda não existem.
 *
 * <p><strong>O que ele NÃO cobre</strong>, e está dito para ninguém confiar demais:
 *
 * <ul>
 *   <li>SQL montado por concatenação ({@code "SELECT " + COLUMNS + " FROM ..."}) — o texto não está
 *       inteiro no fonte, e adivinhar o que a constante contém daria falso positivo. O defeito que
 *       originou o teste estava num bloco de texto-bloco, que é como o projeto escreve a maioria.
 *   <li>Cadeias que ligam por {@code .params(Map)} ou {@code paramSource(...)}: ali os nomes não estão
 *       no fonte como literal. São puladas, e não reprovadas.
 *   <li>O <em>valor</em> ligado estar certo. Contra isso o que existe são os ITs de cada módulo.
 * </ul>
 */
class BoundParametersTest {

    private static final Path SOURCE = Path.of("src/main/java");

    /** Blocos {@code .sql(""" ... """)} seguidos da cadeia fluente até o fim da instrução. */
    private static final Pattern STATEMENT =
            Pattern.compile("\\.sql\\(\\s*\"{3}(.*?)\"{3}\\s*\\)(.*?);", Pattern.DOTALL);

    /**
     * Parâmetro nomeado do Spring: {@code :nome}.
     *
     * <p>O {@code (?<!:)} é obrigatório: {@code ::text} é cast do PostgreSQL, e sem ele toda conversão
     * viraria um parâmetro inexistente — o teste apontaria dezenas de infrações falsas e seria desligado
     * na primeira semana, que é como uma barreira morre.
     */
    private static final Pattern NAMED_PARAM = Pattern.compile("(?<!:):([a-zA-Z][a-zA-Z0-9_]*)");

    private static final Pattern BOUND = Pattern.compile("\\.param\\(\\s*\"([^\"]+)\"");

    /** Ligações que não expõem os nomes no fonte: a cadeia inteira é pulada. */
    private static final Pattern OPAQUE_BINDING = Pattern.compile("\\.(params|paramSource)\\(");

    @Test
    @DisplayName("nenhum SQL usa parâmetro nomeado que a cadeia não liga")
    void todoParametroNomeadoEhLigado() throws IOException {
        var infratores = new ArrayList<String>();
        var conferidas = 0;

        for (var file : javaFiles()) {
            var matcher = STATEMENT.matcher(Files.readString(file));
            while (matcher.find()) {
                var sql = matcher.group(1);
                var chain = matcher.group(2);
                if (OPAQUE_BINDING.matcher(chain).find()) {
                    continue;
                }
                conferidas++;
                var faltando = new LinkedHashSet<>(namesIn(sql, NAMED_PARAM));
                faltando.removeAll(namesIn(chain, BOUND));
                if (!faltando.isEmpty()) {
                    infratores.add(file.getFileName() + " → " + faltando + "\n    "
                            + sql.trim().replaceAll("\\s+", " ").substring(0, Math.min(140, sql.trim().length())));
                }
            }
        }

        // Se a extração quebrar numa refatoração futura, o teste passaria vazio — e teste que passa por
        // não ter olhado nada é pior que teste nenhum, porque parece cobertura.
        assertThat(conferidas)
                .as("instruções SQL com parâmetros conferidas")
                .isGreaterThan(200);

        assertThat(infratores)
                .as("""
                        SQL com parâmetro nomeado que ninguém liga.

                        A instrução falha em tempo de execução com
                        "No value supplied for the SQL parameter", e só quando alguém a executa — o que
                        pode ser meses depois, num processo agendado que ninguém olha.

                        Acrescente o `.param("nome", valor)` correspondente. Se o parâmetro não deveria
                        existir, tire-o do SQL: um filtro esquecido no WHERE é pior que um a menos.
                        """)
                .isEmpty();
    }

    private static Set<String> namesIn(String text, Pattern pattern) {
        var names = new LinkedHashSet<String>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }
}
