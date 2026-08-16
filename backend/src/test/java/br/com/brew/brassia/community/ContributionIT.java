package br.com.brew.brassia.community;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Comentário, sugestão e decisão de ponta a ponta (COM-004).
 *
 * <p>O que estes testes fixam: <strong>aceitar registra concordância e não altera nada</strong>, recusar
 * não apaga, e a conversa é histórico — não caixa de entrada.
 */
@SpringBootTest
@Testcontainers
class ContributionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LIBRARY = "/api/v1/community/library";
    private static final String CONTRIB = "/api/v1/community/contributions";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void oComentarioEContextualENaoSeDecide() throws Exception {
        // Ele não propôs nada: não há o que aceitar. Sem essa regra, a tela ofereceria dois botões sem
        // sentido e a contagem de pendentes incluiria elogios.
        var session = login();
        var publicacao = publica(session);
        var id = escreve(session, publicacao, "COMMENT", "Ficou ótima!", "Malte Pilsen");

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/contributions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].context", is("Malte Pilsen")))
                .andExpect(jsonPath("$[0].pending", is(false)));

        mockMvc.perform(post(CONTRIB + "/" + id + "/accept").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("not_decidable")));
    }

    @Test
    void aceitarRegistraConcordanciaENaoAlteraNada() throws Exception {
        // A decisão central: o retrato publicado é congelado e a receita é privada. Aplicar é ato do
        // autor, na receita dele.
        var session = login();
        var publicacao = publica(session);
        var antes = retrato(session, publicacao);
        var id = escreve(session, publicacao, "SUGGESTION", "Eu subiria o Citra para 30 g", "Lúpulo");

        mockMvc.perform(post(CONTRIB + "/" + id + "/accept").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"note\":\"boa ideia, vou testar\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/contributions").session(session))
                .andExpect(jsonPath("$[0].status", is("ACCEPTED")))
                .andExpect(jsonPath("$[0].decisionNote", is("boa ideia, vou testar")))
                // O texto da sugestão continua o mesmo: aceitar não reescreve o que foi proposto.
                .andExpect(jsonPath("$[0].body", is("Eu subiria o Citra para 30 g")));

        // E o retrato publicado não mudou uma vírgula.
        org.assertj.core.api.Assertions.assertThat(retrato(session, publicacao)).isEqualTo(antes);
    }

    @Test
    void recusarNaoApaga() throws Exception {
        // A sugestão fica visível com a decisão ao lado: é o que evita ela voltar três vezes.
        var session = login();
        var publicacao = publica(session);
        var id = escreve(session, publicacao, "SUGGESTION", "Trocaria a levedura", null);

        mockMvc.perform(post(CONTRIB + "/" + id + "/decline").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"note\":\"prefiro manter o perfil\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/contributions").session(session))
                .andExpect(jsonPath("$[0].status", is("DECLINED")))
                .andExpect(jsonPath("$[0].body", is("Trocaria a levedura")));
    }

    @Test
    void naoSeDecideDuasVezes() throws Exception {
        // Reescreveria quem decidiu e quando — e é esse registro que torna a conversa auditável.
        var session = login();
        var publicacao = publica(session);
        var id = escreve(session, publicacao, "SUGGESTION", "Mais lúpulo", null);

        mockMvc.perform(post(CONTRIB + "/" + id + "/accept").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(CONTRIB + "/" + id + "/decline").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("already_decided")));
    }

    @Test
    void naoSeComentaOQueNaoSePodeLer() throws Exception {
        // A mesma matriz de visibilidade de sempre, e não uma regra nova.
        var session = login();
        var publicacao = publica(session);
        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/visibility").session(session).with(csrf())
                        .contentType("application/json").content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(LIBRARY + "/" + publicacao + "/contributions")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.contribution.write"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"COMMENT\",\"body\":\"oi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void outraCervejariaNaoDecideSobreAPublicacaoAlheia() throws Exception {
        var session = login();
        var publicacao = publica(session);
        var id = escreve(session, publicacao, "SUGGESTION", "Mais lúpulo", null);

        mockMvc.perform(post(CONTRIB + "/" + id + "/accept")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.recipe.publish"))))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void esconderTiraDaListaSemApagar() throws Exception {
        // Moderação precisa poder ser revista, e texto apagado não se revisa.
        var session = login();
        var publicacao = publica(session);
        var id = escreve(session, publicacao, "COMMENT", "spam spam spam", null);

        mockMvc.perform(post(CONTRIB + "/" + id + "/hide").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/contributions").session(session))
                .andExpect(jsonPath("$.length()", is(0)));

        // O texto continua no banco: escondido não é apagado.
        var body = jdbc.sql("SELECT body FROM community_contribution WHERE id = :i")
                .param("i", UUID.fromString(id)).query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(body).isEqualTo("spam spam spam");
    }

    @Test
    void aRespostaNaoCarregaCervejariaNemIdentificadorDeUsuario() throws Exception {
        // Quem lê vê o nome, e nada que permita cruzar aquela pessoa com outra coisa da plataforma.
        var session = login();
        var publicacao = publica(session);
        escreve(session, publicacao, "COMMENT", "Boa!", null);

        var corpo = mockMvc.perform(get(LIBRARY + "/" + publicacao + "/contributions").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo)
                .doesNotContain("breweryId").doesNotContain("authorUserId");
        org.assertj.core.api.Assertions.assertThat(corpo).contains("\"author\"");
    }

    // --- cenário ---

    private String escreve(MockHttpSession session, String publicacao, String kind, String body,
            String context) throws Exception {
        var corpo = context == null
                ? "{\"kind\":\"%s\",\"body\":\"%s\"}".formatted(kind, body)
                : "{\"kind\":\"%s\",\"body\":\"%s\",\"context\":\"%s\"}".formatted(kind, body, context);
        var resposta = mockMvc.perform(post(LIBRARY + "/" + publicacao + "/contributions")
                        .session(session).with(csrf()).contentType("application/json").content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(resposta).get("id").asText();
    }

    private String retrato(MockHttpSession session, String publicacao) throws Exception {
        var corpo = mockMvc.perform(get(LIBRARY + "/" + publicacao).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(corpo).get("recipe").toString();
    }

    private String publica(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maiusculo = sfx.toUpperCase(java.util.Locale.ROOT);
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-%s","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """.formatted(maiusculo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var ingrediente = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"MALTE-%s","name":"Malte Pilsen %s","useUnit":"KG",
                                 "purchaseUnit":"KG","attributes":{"potentialSg":"1.037","colorEbc":"3"}}
                                """.formatted(maiusculo, sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var receita = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"IPA %s","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                                """.formatted(sfx, equipamento, ingrediente)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + receita + "/publish").session(session).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        var body = mockMvc.perform(post(LIBRARY).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"recipeId":"%s","title":"IPA %s","license":"CC0","visibility":"PUBLIC"}
                                """.formatted(receita, sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
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
