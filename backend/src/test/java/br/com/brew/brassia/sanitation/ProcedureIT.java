package br.com.brew.brassia.sanitation;

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
class ProcedureIT {

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

    private static String body(String code, String name) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"steps\":["
                + "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1,"
                + "\"concentrationMaxPct\":2,\"tempMinC\":60,\"tempMaxC\":80,\"timeMinutes\":20,"
                + "\"flow\":\"recirculação\",\"ppe\":\"luvas\",\"alternative\":\"enzimático\","
                + "\"prohibition\":\"não misturar com ácido\",\"evidenceRequired\":true}]}";
    }

    @Test
    void createsEditsAndPublishesThenNewVersion() throws Exception {
        var session = login();
        var code = "CIP-" + shortId();

        var id = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json").content(body(code, "CIP de tanque")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(1)))
                .andReturn().getResponse().getContentAsString());

        // Rascunho editável.
        mockMvc.perform(put("/api/v1/sanitation/procedures/" + id).session(session).with(csrf())
                        .contentType("application/json").content(body(code, "CIP de tanque (rev)")))
                .andExpect(status().isOk());

        // Publicar congela.
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sanitation/procedures/" + id).session(session))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.name", is("CIP de tanque (rev)")));

        // Editar publicado → 409.
        mockMvc.perform(put("/api/v1/sanitation/procedures/" + id).session(session).with(csrf())
                        .contentType("application/json").content(body(code, "não deveria")))
                .andExpect(status().isConflict());

        // Novo POST com o mesmo código (já publicado) → versão 2.
        mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json").content(body(code, "CIP de tanque v2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void rejectsSecondDraftForSameCode() throws Exception {
        var session = login();
        var code = "CIP-" + shortId();
        mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json").content(body(code, "v1")))
                .andExpect(status().isCreated());
        // Já há um rascunho aberto para o código → 409.
        mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json").content(body(code, "outro rascunho")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvertedConcentrationRange() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"CIP-" + shortId() + "\",\"name\":\"x\",\"steps\":["
                                + "{\"sequence\":1,\"method\":\"CIP\",\"concentrationMinPct\":2,"
                                + "\"concentrationMaxPct\":1,\"evidenceRequired\":false}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/sanitation/procedures")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sanitation.procedure.read")))).with(csrf())
                        .contentType("application/json").content(body("CIP-" + shortId(), "x")))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
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
