package br.com.brew.brassia.sanitation;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CompatibilityMatrixIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsRuleAndRecommendsExactMaterialNoInheritance() throws Exception {
        var session = login();
        // Regra específica de produto anterior para INOX/PESADA/ALTO.
        createRule(session, "{\"material\":\"INOX\",\"soiling\":\"PESADA\",\"risk\":\"ALTO\","
                + "\"previousProduct\":\"Lúpulo\",\"method\":\"CIP cáustico 2%\","
                + "\"alternative\":\"detergente enzimático (restrição: sem ácido junto)\","
                + "\"restriction\":\"não misturar com ácido\"}");
        // Regra genérica (sem produto anterior) para o mesmo material/contexto.
        createRule(session, "{\"material\":\"INOX\",\"soiling\":\"PESADA\",\"risk\":\"ALTO\","
                + "\"method\":\"CIP cáustico padrão\"}");

        // Recomenda a específica quando o produto anterior casa.
        mockMvc.perform(post("/api/v1/sanitation/matrix/recommend").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"material\":\"INOX\",\"soiling\":\"PESADA\",\"risk\":\"ALTO\","
                                + "\"previousProduct\":\"lúpulo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("CIP cáustico 2%")))
                .andExpect(jsonPath("$.restriction", is("não misturar com ácido")));

        // Produto anterior sem regra específica → cai na genérica.
        mockMvc.perform(post("/api/v1/sanitation/matrix/recommend").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"material\":\"INOX\",\"soiling\":\"PESADA\",\"risk\":\"ALTO\","
                                + "\"previousProduct\":\"mel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("CIP cáustico padrão")));

        // Madeira não herda inox → sem recomendação (400).
        mockMvc.perform(post("/api/v1/sanitation/matrix/recommend").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"material\":\"MADEIRA\",\"soiling\":\"PESADA\",\"risk\":\"ALTO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateKey() throws Exception {
        var session = login();
        var body = "{\"material\":\"PLASTICO\",\"soiling\":\"LEVE\",\"risk\":\"BAIXO\",\"method\":\"manual\"}";
        createRule(session, body);
        mockMvc.perform(post("/api/v1/sanitation/matrix").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnpublishedProcedureReference() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/sanitation/matrix").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"material\":\"VIDRO\",\"soiling\":\"LEVE\",\"risk\":\"BAIXO\","
                                + "\"procedureCode\":\"NAO-EXISTE\",\"method\":\"manual\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/sanitation/matrix")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sanitation.matrix.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"material\":\"INOX\",\"soiling\":\"LEVE\",\"risk\":\"BAIXO\",\"method\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private void createRule(MockHttpSession session, String body) throws Exception {
        mockMvc.perform(post("/api/v1/sanitation/matrix").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
