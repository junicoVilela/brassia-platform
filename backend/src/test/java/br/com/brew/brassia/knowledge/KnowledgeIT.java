package br.com.brew.brassia.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de conhecimento de ponta a ponta (RAG-001).
 *
 * <p>Aqui está o que nenhum teste de unidade cobre: a busca textual em português de verdade — radical,
 * acento, ranqueamento — e o filtro de permissão dentro da consulta. Os dois só existem no SQL, e é
 * exatamente sobre eles que os critérios da história falam.
 */
@SpringBootTest
@Testcontainers
class KnowledgeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KNOWLEDGE = "/api/v1/knowledge";

    /** Permissão de um documento restrito; o admin de bootstrap não a tem. */
    private static final String RESTRICTED = "quality.sample.read";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("indexar corta o documento em trechos e devolve a versão 1 vigente")
    void indexaPrimeiraVersao() throws Exception {
        var session = login();
        var code = uniqueCode("FISPQ");

        var created = index(session, code, "FISPQ — Ácido peracético", "2026-04-01",
                "knowledge.document.read", """
                        O ácido peracético é utilizado na sanitização de tanques e tubulações.

                        A concentração recomendada é de 0,15% em volume, com tempo de contato mínimo
                        de vinte minutos na temperatura ambiente do processo.
                        """);

        created.andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.current", org.hamcrest.Matchers.is(true)))
                .andExpect(jsonPath("$.effectiveTo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.chunks", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("a busca acha pelo radical da palavra: 'sanitizar' encontra 'sanitização'")
    void buscaPorRadical() throws Exception {
        // É o que o dicionário português entrega e a razão de a busca não ser um LIKE: quem pergunta não
        // usa a mesma flexão que o documento escreveu.
        var session = login();
        var code = uniqueCode("FISPQ");
        index(session, code, "FISPQ — Peracético", "2026-04-01", "knowledge.document.read", """
                O ácido peracético é utilizado na sanitização de tanques.
                A concentração recomendada é de 0,15% em volume.
                """).andExpect(status().isCreated());

        var hits = search(session, "como sanitizar tanque com peracetico");

        assertThat(hits.size()).isGreaterThanOrEqualTo(1);
        assertThat(hits.get(0).get("text").asText()).contains("peracético");
        assertThat(hits.get(0).get("code").asText()).isEqualTo(code);
        // Todo trecho recuperado viaja marcado como não confiável: é texto de terceiro.
        assertThat(hits.get(0).get("untrusted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("documento cuja permissão o consultante não tem não é recuperado — nem o título")
    void semPermissaoNaoRecupera() throws Exception {
        var session = login();
        var code = uniqueCode("LAUDO");
        // Indexado com uma permissão que o admin de bootstrap não possui.
        index(session, code, "Laudo confidencial de contaminação", "2026-04-01", RESTRICTED, """
                A análise identificou contaminação por Lactobacillus no tanque de maturação.
                """).andExpect(status().isCreated());

        // O admin indexou (tem knowledge.document.index) mas não pode LER este documento. A busca pode
        // devolver outros documentos que ele pode ler — o que se afirma é que este não vem, em trecho
        // nenhum. Exigir resultado totalmente vazio testaria o banco compartilhado, não a permissão.
        var hits = search(session, "contaminação Lactobacillus tanque maturação");
        assertThat(hits.findValuesAsText("code")).doesNotContain(code);
        assertThat(textOfCode(hits, code)).isEmpty();

        // E ele também não aparece na listagem: um título de laudo já é informação.
        var listed = read(mockMvc.perform(get(KNOWLEDGE + "/documents").session(session))
                .andExpect(status().isOk()));
        assertThat(listed.findValuesAsText("code")).doesNotContain(code);
    }

    @Test
    @DisplayName("com a permissão certa, o mesmo documento é recuperado")
    void comPermissaoRecupera() throws Exception {
        var session = login();
        var code = uniqueCode("LAUDO");
        index(session, code, "Laudo de contaminação", "2026-04-01", RESTRICTED, """
                A análise identificou contaminação por Lactobacillus no tanque de maturação.
                """).andExpect(status().isCreated());

        // Mesma cervejaria, mas agora com a permissão exigida pelo documento.
        var brewery = breweryOf(session);
        var allowed = principal(brewery, Set.of("knowledge.document.read", RESTRICTED));
        var hits = read(mockMvc.perform(get(KNOWLEDGE + "/search")
                        .param("question", "contaminação Lactobacillus maturação")
                        .with(authentication(allowed)))
                .andExpect(status().isOk()));

        assertThat(hits.findValuesAsText("code")).contains(code);
    }

    @Test
    @DisplayName("nova versão encerra a anterior: nunca duas vigentes no mesmo dia")
    void novaVersaoEncerraAnterior() throws Exception {
        var session = login();
        var code = uniqueCode("FISPQ");
        index(session, code, "FISPQ v1", "2026-04-01", "knowledge.document.read",
                "A concentração de peracético recomendada é de 0,15% em volume.")
                .andExpect(status().isCreated());

        index(session, code, "FISPQ v2", "2026-06-01", "knowledge.document.read",
                "A concentração de peracético recomendada passou a ser de 0,20% em volume.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", org.hamcrest.Matchers.is(2)));

        var listed = read(mockMvc.perform(get(KNOWLEDGE + "/documents").session(session))
                .andExpect(status().isOk()));
        var versions = versionsOf(listed, code);
        assertThat(versions.get(1).get("effectiveTo").asText()).isEqualTo("2026-05-31");
        assertThat(versions.get(1).get("current").asBoolean()).isFalse();
        assertThat(versions.get(2).get("current").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a busca de hoje devolve a versão vigente; a de uma data passada devolve a que valia")
    void buscaRespeitaAVigenciaNaData() throws Exception {
        // Esta é a pergunta que a história precisa saber responder: o que valia quando o lote foi feito.
        var session = login();
        var code = uniqueCode("FISPQ");
        index(session, code, "FISPQ v1", "2026-04-01", "knowledge.document.read",
                "A concentração de peracético recomendada é de 0,15% em volume.")
                .andExpect(status().isCreated());
        index(session, code, "FISPQ v2", "2026-06-01", "knowledge.document.read",
                "A concentração de peracético recomendada passou a ser de 0,20% em volume.")
                .andExpect(status().isCreated());

        // Recortado por código: os testes compartilham o banco, e outros documentos falam do mesmo
        // assunto. O que se afirma aqui é sobre as duas versões DESTE documento.
        var hoje = textOfCode(search(session, "concentração peracético recomendada", "2026-07-01"), code);
        assertThat(hoje).contains("0,20%").doesNotContain("0,15%");

        var emMaio = textOfCode(search(session, "concentração peracético recomendada", "2026-05-01"), code);
        assertThat(emMaio).contains("0,15%").doesNotContain("0,20%");
    }

    @Test
    @DisplayName("filtro de equipamento aceita o manual dele e o que não é de equipamento nenhum")
    void filtraPorEquipamento() throws Exception {
        var session = login();
        var bomba = UUID.randomUUID();
        var caldeira = UUID.randomUUID();
        var manualBomba = uniqueCode("MAN-BOMBA");
        var manualCaldeira = uniqueCode("MAN-CALD");
        var geral = uniqueCode("PROC-GERAL");

        indexFor(session, manualBomba, "Manual da bomba centrífuga", bomba,
                "O torque de aperto do parafuso da bomba centrífuga é de 25 newton-metro.");
        indexFor(session, manualCaldeira, "Manual da caldeira", caldeira,
                "O torque de aperto do parafuso da caldeira é de 40 newton-metro.");
        indexFor(session, geral, "Procedimento geral de aperto", null,
                "Todo torque de aperto deve ser conferido com torquímetro calibrado.");

        var hits = read(mockMvc.perform(get(KNOWLEDGE + "/search")
                        .param("question", "torque de aperto do parafuso")
                        .param("equipmentId", bomba.toString())
                        .session(session))
                .andExpect(status().isOk()));

        var codes = hits.findValuesAsText("code");
        assertThat(codes).contains(manualBomba);
        // O procedimento geral responde sobre a bomba tanto quanto o manual dela.
        assertThat(codes).contains(geral);
        // O manual da caldeira não responde sobre a bomba.
        assertThat(codes).doesNotContain(manualCaldeira);
    }

    @Test
    @DisplayName("pergunta sem fonte devolve lista vazia — é resposta legítima, não erro")
    void semFonteDevolveVazio() throws Exception {
        var session = login();

        var hits = search(session, "criogenia supercondutora aplicada a levitação magnética");

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("caracteres de sintaxe na pergunta não viram operador nem erro")
    void perguntaComSintaxeNaoQuebra() throws Exception {
        // `plainto_tsquery` trata a entrada como texto. Sem isso, um `&` ou parêntese na pergunta viraria
        // erro de parsing — e a mesma porta serviria para injetar sintaxe de busca.
        var session = login();

        var hits = search(session, "peracético & (concentração | 'quebra') !: <-> ???");

        assertThat(hits).isNotNull();
    }

    @Test
    @DisplayName("documento de outra cervejaria não é recuperado nem listado")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var code = uniqueCode("FISPQ");
        index(session, code, "FISPQ — Peracético", "2026-04-01", "knowledge.document.read",
                "A concentração de peracético recomendada é de 0,15% em volume.")
                .andExpect(status().isCreated());

        var other = principal(UUID.randomUUID(),
                Set.of("knowledge.document.read", "knowledge.document.index"));

        var hits = read(mockMvc.perform(get(KNOWLEDGE + "/search")
                        .param("question", "concentração peracético")
                        .with(authentication(other)))
                .andExpect(status().isOk()));
        assertThat(hits).isEmpty();

        var listed = read(mockMvc.perform(get(KNOWLEDGE + "/documents").with(authentication(other)))
                .andExpect(status().isOk()));
        assertThat(listed).isEmpty();
    }

    @Test
    @DisplayName("indexar é alçada própria: quem só lê não muda a fonte das respostas")
    void indexarExigeAlcadaPropria() throws Exception {
        var reader = principal(UUID.randomUUID(), Set.of("knowledge.document.read"));

        mockMvc.perform(get(KNOWLEDGE + "/documents").with(authentication(reader)))
                .andExpect(status().isOk());
        mockMvc.perform(post(KNOWLEDGE + "/documents").with(csrf()).with(authentication(reader))
                        .contentType("application/json")
                        .content(indexBody("X-1", "Título", "2026-04-01", "knowledge.document.read",
                                null, "texto suficiente para indexar")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissaoNadaResponde() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(KNOWLEDGE + "/documents").with(authentication(nobody)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(KNOWLEDGE + "/search").param("question", "peracético")
                        .with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("documento sem texto indexável é recusado no contrato")
    void textoVazioEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(post(KNOWLEDGE + "/documents").with(csrf()).session(session)
                        .contentType("application/json")
                        .content(indexBody(uniqueCode("X"), "Título", "2026-04-01",
                                "knowledge.document.read", null, "   ")))
                .andExpect(status().isBadRequest());
    }

    // --- infraestrutura ---

    private ResultActions index(MockHttpSession session, String code, String title, String from,
            String permission, String text) throws Exception {
        return mockMvc.perform(post(KNOWLEDGE + "/documents").with(csrf()).session(session)
                .contentType("application/json")
                .content(indexBody(code, title, from, permission, null, text)));
    }

    private void indexFor(MockHttpSession session, String code, String title, UUID equipmentId,
            String text) throws Exception {
        mockMvc.perform(post(KNOWLEDGE + "/documents").with(csrf()).session(session)
                        .contentType("application/json")
                        .content(indexBody(code, title, "2026-04-01", "knowledge.document.read",
                                equipmentId, text)))
                .andExpect(status().isCreated());
    }

    private static String indexBody(String code, String title, String from, String permission,
            UUID equipmentId, String text) throws Exception {
        var node = JSON.createObjectNode();
        node.put("type", "SAFETY_DATA_SHEET");
        node.put("code", code);
        node.put("title", title);
        node.put("effectiveFrom", from);
        node.put("requiredPermission", permission);
        node.put("text", text);
        if (equipmentId != null) {
            node.put("equipmentId", equipmentId.toString());
        }
        return JSON.writeValueAsString(node);
    }

    private JsonNode search(MockHttpSession session, String question) throws Exception {
        return search(session, question, null);
    }

    private JsonNode search(MockHttpSession session, String question, String onDate) throws Exception {
        var request = get(KNOWLEDGE + "/search").param("question", question).session(session);
        if (onDate != null) {
            request = request.param("onDate", onDate);
        }
        return read(mockMvc.perform(request).andExpect(status().isOk()));
    }

    /** Texto dos trechos de um documento só — os testes compartilham o banco. */
    private static String textOfCode(JsonNode hits, String code) {
        var joined = new StringBuilder();
        hits.forEach(hit -> {
            if (code.equals(hit.get("code").asText())) {
                joined.append(hit.get("text").asText()).append('\n');
            }
        });
        return joined.toString();
    }

    /** As versões de um código, indexadas pelo número da versão. */
    private static java.util.Map<Integer, JsonNode> versionsOf(JsonNode documents, String code) {
        var byVersion = new java.util.HashMap<Integer, JsonNode>();
        documents.forEach(doc -> {
            if (code.equals(doc.get("code").asText())) {
                byVersion.put(doc.get("version").asInt(), doc);
            }
        });
        return byVersion;
    }

    /** Código único por teste: os testes compartilham o banco, e código é chave de versionamento. */
    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = read(mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()));
        return UUID.fromString(body.get("activeBrewery").get("id").asText());
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
