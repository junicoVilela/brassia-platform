package br.com.brew.brassia.sensory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
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
 * Biblioteca de descritores de ponta a ponta (SEN-002).
 *
 * <p>O que só aparece aqui: a busca por sinônimo atravessando o SQL com a normalização dos <em>dois</em>
 * lados, e a recusa do limiar sem licença acontecendo no banco além do domínio — a regra vale para quem
 * entra pela aplicação e para quem entra por carga direta.
 */
@SpringBootTest
@Testcontainers
class DescriptorLibraryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DESCRIPTORS = "/api/v1/sensory/descriptors";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("descritor próprio aceita limiar e é exportável")
    void fontePropriaComLimiar() throws Exception {
        var session = login();

        criar(session, "papelao-" + sufixo(), "Papelão", "OFF_FLAVOR", "OWN", null, "0.05", "mg/L")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perceptionThreshold").value(0.05))
                .andExpect(jsonPath("$.exportable").value(true))
                .andExpect(jsonPath("$.licenseTier").value("OWN"));
    }

    @Test
    @DisplayName("LIMIAR SEM LICENÇA é recusado")
    void limiarSemLicencaRecusado() throws Exception {
        // Recusado na criação, não filtrado na leitura: um dado que não pode ser publicado e mesmo assim
        // está gravado é um vazamento esperando exportação.
        var session = login();

        criar(session, "diacetil-" + sufixo(), "Diacetil", "OFF_FLAVOR",
                "LICENSED_INTERNAL_ONLY", "© Catálogo X", "0.1", "mg/L")
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("licença restrita SEM limiar é aceita, e não é exportável")
    void licencaRestritaSemLimiar() throws Exception {
        // O vocabulário licenciado serve para anotar e comparar internamente; republicá-lo é outra coisa.
        var session = login();

        criar(session, "dms-" + sufixo(), "DMS", "OFF_FLAVOR",
                "LICENSED_INTERNAL_ONLY", "© Catálogo X", null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exportable").value(false))
                .andExpect(jsonPath("$.attribution").value("© Catálogo X"));
    }

    @Test
    @DisplayName("licença com atribuição EXIGE o texto")
    void atribuicaoObrigatoria() throws Exception {
        var session = login();

        criar(session, "banana-" + sufixo(), "Banana", "ATTRIBUTE",
                "ATTRIBUTION_REQUIRED", null, null, null)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("A BUSCA ENCONTRA PELO SINÔNIMO, sem acento e sem caixa")
    void buscaPorSinonimo() throws Exception {
        // Quem anota na mesa de prova digita rápido. Um vocabulário que só acha o termo exato não serve
        // para o momento em que é usado — com a taça na mão.
        var session = login();
        var codigo = "papelao-" + sufixo();
        criar(session, codigo, "Papelão", "OFF_FLAVOR", "OWN", null, null, null)
                .andExpect(status().isCreated());

        for (var termo : new String[] {"cartonado", "CARTONADO", "Cartonádo", "papelao", "Papelão"}) {
            mockMvc.perform(get(DESCRIPTORS).param("term", termo).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.code=='" + codigo.toUpperCase() + "')]").exists());
        }
    }

    @Test
    @DisplayName("as hipóteses vêm com COMO VERIFICAR")
    void hipotesesComVerificacao() throws Exception {
        // "Pode ser infecção" sem dizer como confirmar deixa quem lê com a preocupação e sem o próximo
        // passo.
        var session = login();

        criar(session, "acido-" + sufixo(), "Acidez láctica", "OFF_FLAVOR", "OWN", null, null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hypotheses[0].suggestedCheck").isNotEmpty())
                .andExpect(jsonPath("$.hypotheses[0].likelihood").isNotEmpty());
    }

    @Test
    @DisplayName("O MESMO DESCRITOR muda de papel conforme o estilo")
    void papelDependeDoEstilo() throws Exception {
        // Banana é atributo numa Weissbier e desvio numa Pilsen. Um vocabulário que não distingue isso
        // ensina errado justamente no treinamento.
        var session = login();
        var body = criar(session, "banana-" + sufixo(), "Banana", "ATTRIBUTE", "OWN", null, null, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(body).get("id").asText();

        mockMvc.perform(post(DESCRIPTORS + "/" + id + "/styles/WEISS").param("expected", "true")
                .session(session).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post(DESCRIPTORS + "/" + id + "/styles/PILSEN").param("expected", "false")
                .session(session).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(get(DESCRIPTORS + "/by-style/WEISS").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expected").value(true));
        mockMvc.perform(get(DESCRIPTORS + "/by-style/PILSEN").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expected").value(false));
    }

    @Test
    @DisplayName("descritor de outra cervejaria não aparece")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var codigo = "isolado-" + sufixo();
        criar(session, codigo, "Isolado", "ATTRIBUTE", "OWN", null, null, null)
                .andExpect(status().isCreated());

        var outra = principal(UUID.randomUUID(), Set.of("sensory.descriptor.read"));

        mockMvc.perform(get(DESCRIPTORS).param("term", codigo).with(authentication(outra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("escrever exige permissão própria")
    void escritaExigePermissao() throws Exception {
        var somenteLeitura = principal(UUID.randomUUID(), Set.of("sensory.descriptor.read"));

        mockMvc.perform(post(DESCRIPTORS).with(csrf()).with(authentication(somenteLeitura))
                        .contentType("application/json").content(corpo("x", "X", "ATTRIBUTE",
                                "OWN", null, null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissao() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(DESCRIPTORS).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    // --- infraestrutura ---

    private ResultActions criar(MockHttpSession session, String code, String name, String category,
            String tier, String attribution, String threshold, String unit) throws Exception {
        return mockMvc.perform(post(DESCRIPTORS).session(session).with(csrf())
                .contentType("application/json")
                .content(corpo(code, name, category, tier, attribution, threshold, unit)));
    }

    private static String corpo(String code, String name, String category, String tier,
            String attribution, String threshold, String unit) {
        return """
                {"code":"%s","name":"%s","category":"%s","synonyms":["cartonado","molhado"],
                 "sourceName":"Painel interno","licenseTier":"%s"%s%s%s,
                 "hypotheses":[{"possibleCause":"Oxidação no envase",
                                "suggestedCheck":"Conferir oxigênio dissolvido do lote",
                                "likelihood":"COMMON"}]}
                """.formatted(code, name, category, tier,
                attribution == null ? "" : ",\"attribution\":\"" + attribution + "\"",
                threshold == null ? "" : ",\"perceptionThreshold\":" + threshold,
                unit == null ? "" : ",\"thresholdUnit\":\"" + unit + "\"");
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
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
