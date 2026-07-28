package br.com.brew.brassia.fermentation;

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
class ProfileIT {

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

    private static final String TIME_STAGE = "{\"sequence\":1,\"name\":\"Primária\",\"targetTempC\":18.0,"
            + "\"rampHours\":4,\"condition\":\"TIME\",\"conditionDays\":5,\"requiresConfirmation\":true}";
    private static final String GRAVITY_STAGE = "{\"sequence\":2,\"name\":\"Diacetil\",\"targetTempC\":20.0,"
            + "\"condition\":\"GRAVITY\",\"targetGravity\":1.012,\"requiresConfirmation\":true}";

    @Test
    void createsVersionsAndFreezesOnPublish() throws Exception {
        var session = login();
        var code = "ALE-" + UUID.randomUUID().toString().substring(0, 6);

        var id = createProfile(session, code, "[" + TIME_STAGE + "," + GRAVITY_STAGE + "]", 1);
        // Editar rascunho é permitido.
        mockMvc.perform(put("/api/v1/fermentation/profiles/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Ale ajustada\",\"stages\":[" + TIME_STAGE + "]}"))
                .andExpect(status().isOk());
        // Segundo rascunho do mesmo código → 409.
        mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Outra\",\"stages\":[" + TIME_STAGE + "]}"))
                .andExpect(status().isConflict());

        // Publicar congela; editar publicado → 409; novo POST gera v2.
        mockMvc.perform(post("/api/v1/fermentation/profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/fermentation/profiles/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"stages\":[" + TIME_STAGE + "]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Ale v2\",\"stages\":[" + TIME_STAGE + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void rejectsGravityStageWithoutTargetGravity() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"BAD\",\"name\":\"Bad\",\"stages\":[{\"sequence\":1,\"name\":\"D\","
                                + "\"targetTempC\":20,\"condition\":\"GRAVITY\",\"requiresConfirmation\":true}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/fermentation/profiles")
                        .with(authentication(principal(Set.of("fermentation.profile.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"X\",\"name\":\"X\",\"stages\":[" + TIME_STAGE + "]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var code = "ISO-" + UUID.randomUUID().toString().substring(0, 6);
        var id = createProfile(session, code, "[" + TIME_STAGE + "]", 1);
        mockMvc.perform(get("/api/v1/fermentation/profiles/" + id)
                        .with(authentication(principal(Set.of("fermentation.profile.read")))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String createProfile(MockHttpSession session, String code, String stagesJson, int expectedVersion)
            throws Exception {
        var body = mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Ale\",\"stages\":" + stagesJson + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(expectedVersion)))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
