package br.com.brew.brassia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Todo caminho publicado no contrato é servido por um controlador (DEB-INT-004).
 *
 * <p><strong>Por que este teste existe.</strong> O {@code openapi.yaml} publicava
 * {@code POST /api/v1/batches/{batchId}/measurements} — um rascunho antigo do endpoint que hoje vive em
 * {@code /api/v1/production/batches/{id}/measurements}. Nenhum controlador o servia. Quem integrasse pelo
 * contrato bateria num 404, e nada no build reclamava: o contrato é conferido contra si mesmo (é YAML
 * válido, os {@code $ref} resolvem), nunca contra o código.
 *
 * <p>Contrato e implementação divergindo em silêncio é pior que contrato ausente. Um contrato ausente
 * manda perguntar; um contrato errado manda confiar.
 *
 * <p><strong>Só uma direção é conferida aqui</strong>, e isso é decisão, não omissão: contrato → código.
 * A direção oposta — código sem contrato — pega endpoint interno legítimo (actuator, callbacks de SSO) e
 * daria falso positivo, que é como um portão morre. Se ela for desejada um dia, precisa de uma lista de
 * exceções, e lista de exceção envelhece.
 *
 * <p>Os nomes dos parâmetros são <strong>normalizados</strong>: o contrato diz {@code {batchId}} onde o
 * controlador diz {@code {id}}, e as duas coisas descrevem a mesma rota. Comparar nome de parâmetro
 * transformaria divergência de vocabulário em falha de build, que não é o que se quer barrar.
 */
class PublishedPathsTest {

    private static final Path SOURCE = Path.of("src/main/java");
    private static final Path CONTRACT = Path.of("../contracts/openapi.yaml");

    private static final Pattern CLASS_MAPPING =
            Pattern.compile("@RequestMapping\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\")?");
    /** Caminhos do contrato: chaves de dois espaços dentro de `paths:`. */
    private static final Pattern CONTRACT_PATH = Pattern.compile("(?m)^  (/[^\\s:]*):");

    @Test
    @DisplayName("nenhum caminho do contrato fica sem controlador que o sirva")
    void todoCaminhoPublicadoTemControlador() throws IOException {
        var servidos = rotasDoCodigo();
        var publicados = caminhosDoContrato();

        // Se a extração quebrar numa refatoração, os dois lados vêm vazios e o teste passaria sem ter
        // olhado nada — que é pior que teste nenhum, porque parece cobertura.
        assertThat(servidos).as("rotas extraídas dos controladores").hasSizeGreaterThan(200);
        assertThat(publicados).as("caminhos extraídos do contrato").hasSizeGreaterThan(300);

        var fantasmas = new ArrayList<String>();
        for (var path : publicados) {
            if (!servidos.contains(normaliza(path))) {
                fantasmas.add(path);
            }
        }

        assertThat(fantasmas)
                .as("""
                        O contrato publica caminho que nenhum controlador serve.

                        Quem integrar por ele recebe 404. Ou o endpoint foi renomeado e o contrato ficou
                        para trás — apague a entrada —, ou ele deveria existir e não foi escrito.

                        Se a rota existe mas o padrão não a reconhece (mapeamento montado por constante,
                        por exemplo), o certo é ajustar a extração aqui, e não afrouxar a asserção.
                        """)
                .isEmpty();
    }

    /** Todas as rotas {@code base + sufixo} declaradas por anotação nos controladores. */
    private static Set<String> rotasDoCodigo() throws IOException {
        var rotas = new LinkedHashSet<String>();
        for (var file : javaFiles()) {
            var source = Files.readString(file);
            var classe = CLASS_MAPPING.matcher(source);
            var base = classe.find() ? classe.group(1) : "";
            var metodo = METHOD_MAPPING.matcher(source);
            while (metodo.find()) {
                var sufixo = metodo.group(2) == null ? "" : metodo.group(2);
                rotas.add(normaliza(junta(base, sufixo)));
            }
        }
        return rotas;
    }

    private static List<String> caminhosDoContrato() throws IOException {
        var texto = Files.readString(CONTRACT);
        var paths = new ArrayList<String>();
        Matcher matcher = CONTRACT_PATH.matcher(texto);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    private static String junta(String base, String sufixo) {
        var completo = base + (sufixo.startsWith("/") || sufixo.isEmpty() ? sufixo : "/" + sufixo);
        return completo.endsWith("/") && completo.length() > 1
                ? completo.substring(0, completo.length() - 1)
                : completo;
    }

    /**
     * Tira o prefixo da API e apaga o nome dos parâmetros.
     *
     * <p>{@code /api/v1/production/batches/{id}} e {@code /production/batches/{batchId}} descrevem a
     * mesma rota; só o vocabulário difere. O {@code :regex} de um {@code @PathVariable} também some.
     */
    private static String normaliza(String path) {
        var semPrefixo = path.startsWith("/api/v1") ? path.substring("/api/v1".length()) : path;
        return semPrefixo.replaceAll("\\{[^}]*\\}", "{}");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }
}
