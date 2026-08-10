package br.com.brew.brassia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Toda escrita em tabela multi-tenant filtra por {@code brewery_id} (OBS-REL-001).
 *
 * <p><strong>Por que este teste existe.</strong> A auditoria da REL-003 encontrou dez {@code UPDATE} e
 * {@code DELETE} que tocavam tabelas com {@code brewery_id} filtrando só pelo id. Nenhum era
 * vulnerabilidade — todos tinham guarda no handler, que carregava a entidade com escopo antes. Mas a
 * garantia morava inteira na disciplina de cada handler lembrar de carregar antes, e um handler novo que
 * receba o id do path e chame o repositório direto vaza entre cervejarias sem que nada reclame.
 *
 * <p>Corrigir os dez foi metade do trabalho. Esta é a outra metade: transforma a checagem que fiz uma vez,
 * na mão, em barreira que roda a cada build. O padrão da plataforma é tornar o erro impossível de escrever
 * em vez de pedi-lo na revisão — e aqui o custo disso é uma varredura de texto.
 *
 * <p><strong>O que ele NÃO garante.</strong> Que o {@code brewery_id} usado seja o da sessão. Um
 * repositório que receba o id errado passa por aqui. Esta é a barreira contra <em>esquecer o filtro</em>,
 * que era o problema real encontrado; contra <em>usar o filtro errado</em>, o que existe são os testes de
 * integração que cada módulo tem para "outra cervejaria não enxerga".
 */
class TenantIsolationTest {

    private static final Path SOURCE = Path.of("src/main/java");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** Blocos {@code .sql(""" ... """)} — a única forma como o projeto escreve SQL. */
    private static final Pattern SQL_BLOCK = Pattern.compile("\\.sql\\(\\s*\"{3}(.*?)\"{3}", Pattern.DOTALL);
    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL);
    private static final Pattern WRITE = Pattern.compile("^(update|delete)\\b");

    @Test
    @DisplayName("nenhum UPDATE ou DELETE toca tabela com brewery_id sem filtrar por ela")
    void escritasFiltramPorCervejaria() throws IOException {
        var tenantTables = tenantTables();
        // Se a extração falhar, o teste passaria vazio — e um teste que passa por não ter olhado nada é
        // pior que teste nenhum, porque parece cobertura.
        assertThat(tenantTables)
                .as("tabelas com brewery_id extraídas das migrations")
                .hasSizeGreaterThan(100);

        var infratores = new ArrayList<String>();
        for (var file : javaFiles()) {
            var source = Files.readString(file);
            var matcher = SQL_BLOCK.matcher(source);
            while (matcher.find()) {
                var statement = normalize(matcher.group(1));
                if (!WRITE.matcher(statement.toLowerCase()).find()) {
                    continue;
                }
                var touched = tenantTables.stream()
                        .filter(table -> mentions(statement, table))
                        .toList();
                if (!touched.isEmpty() && !statement.toLowerCase().contains("brewery")) {
                    infratores.add(file.getFileName() + " → " + touched + "\n    "
                            + statement.substring(0, Math.min(120, statement.length())));
                }
            }
        }

        assertThat(infratores)
                .as("""
                        Escrita em tabela multi-tenant sem filtro por brewery_id.

                        Acrescente `AND brewery_id = :brewery` ao WHERE e propague o id do handler — que
                        já o tem, porque toda operação nasce de um principal com cervejaria ativa.

                        Se a tabela é filha e o escopo vem do pai (por exemplo, passos de um lote), a
                        escrita ainda deve citar a cervejaria: a junção com o pai custa pouco e não
                        depende de quem chama ter carregado o pai com escopo antes.
                        """)
                .isEmpty();
    }

    /** Nomes de tabela que têm coluna {@code brewery_id}, lidos das migrations. */
    private static Set<String> tenantTables() throws IOException {
        var all = new StringBuilder();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (var file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                all.append(Files.readString(file)).append('\n');
            }
        }
        var tables = new HashSet<String>();
        var matcher = CREATE_TABLE.matcher(all);
        while (matcher.find()) {
            if (matcher.group(2).contains("brewery_id")) {
                tables.add(matcher.group(1));
            }
        }
        return tables;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Casa o nome da tabela como palavra inteira.
     *
     * <p>Sem a fronteira, {@code batch} casaria dentro de {@code production_batch_step} e a varredura
     * apontaria tabelas que a instrução nem menciona — ruído que faz o teste ser desligado.
     */
    private static boolean mentions(String statement, String table) {
        return Pattern.compile("\\b" + Pattern.quote(table) + "\\b")
                .matcher(statement.toLowerCase()).find();
    }

    private static String normalize(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }
}
