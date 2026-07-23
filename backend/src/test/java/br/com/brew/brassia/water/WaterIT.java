package br.com.brew.brassia.water;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
class WaterIT {

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
    void registersSourceRecordsReportsAndKeepsHistory() throws Exception {
        var session = login();
        var sourceId = createSource(session, "poco-1");

        // Primeiro laudo.
        mockMvc.perform(post(reports(sourceId)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"collectedOn":"2026-01-10","method":"LAB","calcium":40,"magnesium":8,
                                 "sodium":12,"sulfate":50,"chloride":30,"bicarbonate":120,"notes":"1o laudo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method", is("LAB")))
                .andExpect(jsonPath("$.calcium", is(40)));

        // Segundo laudo (mais recente) — o antigo permanece disponível.
        mockMvc.perform(post(reports(sourceId)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"collectedOn":"2026-06-20","method":"ION_METER","calcium":55,"magnesium":10,
                                 "sodium":15,"sulfate":60,"chloride":35,"bicarbonate":110}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get(reports(sourceId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].collectedOn", is("2026-06-20"))) // mais recente primeiro
                .andExpect(jsonPath("$[*].collectedOn", hasItem("2026-01-10"))); // laudo antigo preservado
    }

    @Test
    void rejectsInvalidIonMethodAndFutureDate() throws Exception {
        var session = login();
        var sourceId = createSource(session, "poco-2");

        // Íon fora da faixa → 400.
        mockMvc.perform(post(reports(sourceId)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"collectedOn":"2026-01-10","method":"LAB","calcium":99999,"magnesium":8,
                                 "sodium":12,"sulfate":50,"chloride":30,"bicarbonate":120}
                                """))
                .andExpect(status().isBadRequest());

        // Método inválido → 400.
        mockMvc.perform(post(reports(sourceId)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"collectedOn":"2026-01-10","method":"MAGIC","calcium":40,"magnesium":8,
                                 "sodium":12,"sulfate":50,"chloride":30,"bicarbonate":120}
                                """))
                .andExpect(status().isBadRequest());

        // Código de fonte duplicado → 409.
        mockMvc.perform(post("/api/v1/water/sources").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"POCO-2\",\"name\":\"Outro\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesRecordWithoutPermission() throws Exception {
        mockMvc.perform(post(reports(UUID.randomUUID()))
                        .with(authentication(principal(UUID.randomUUID(), Set.of("water.read")))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"collectedOn":"2026-01-10","method":"LAB","calcium":40,"magnesium":8,
                                 "sodium":12,"sulfate":50,"chloride":30,"bicarbonate":120}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesSourcesByBrewery() throws Exception {
        var session = login();
        createSource(session, "poco-3");

        var other = principal(UUID.randomUUID(), Set.of("water.read"));
        mockMvc.perform(get("/api/v1/water/sources").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    private String createSource(MockHttpSession session, String code) throws Exception {
        var body = mockMvc.perform(post("/api/v1/water/sources").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Fonte\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String reports(Object sourceId) {
        return "/api/v1/water/sources/" + sourceId + "/reports";
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
