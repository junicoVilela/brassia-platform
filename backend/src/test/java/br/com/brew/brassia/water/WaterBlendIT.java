package br.com.brew.brassia.water;

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
class WaterBlendIT {

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
    void simulatesBlendAgainstTargetAndReportsInputsAndMethod() throws Exception {
        var session = login();
        var soft = sourceWithCalcium(session, "soft", "40");
        var hard = sourceWithCalcium(session, "hard", "80");
        var target = profileWithCalcium(session, "balanced", "60");

        var body = """
                {"inputs":[{"sourceId":"%s","volumeLiters":100},{"sourceId":"%s","volumeLiters":100}],
                 "targetProfileId":"%s"}
                """.formatted(soft, hard, target);

        mockMvc.perform(post("/api/v1/water/blends/simulate").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("VOLUME_WEIGHTED_AVERAGE")))
                .andExpect(jsonPath("$.totalVolumeLiters", is(200)))
                .andExpect(jsonPath("$.calcium", is(60.0)))         // balanço fecha: (40+80)/2
                .andExpect(jsonPath("$.inputs.length()", is(2)))    // resultado informa entradas
                .andExpect(jsonPath("$.target.deviation.calcium", is(0.0))); // alcança o alvo
    }

    @Test
    void rejectsSourceWithoutReportAndEmptyInputs() throws Exception {
        var session = login();

        // Fonte sem laudo → 400.
        var sourceNoReport = mockMvc.perform(post("/api/v1/water/sources").session(session).with(csrf())
                        .contentType("application/json").content("{\"code\":\"dry\",\"name\":\"Sem laudo\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(sourceNoReport).get("id").asText();

        mockMvc.perform(post("/api/v1/water/blends/simulate").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputs\":[{\"sourceId\":\"" + id + "\",\"volumeLiters\":100}]}"))
                .andExpect(status().isBadRequest());

        // Sem entradas → 400.
        mockMvc.perform(post("/api/v1/water/blends/simulate").session(session).with(csrf())
                        .contentType("application/json").content("{\"inputs\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesProfileCreationWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/water/profiles")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("water.read")))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"x","name":"X","calcium":10,"magnesium":0,"sodium":0,"sulfate":0,
                                 "chloride":0,"bicarbonate":0}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesProfilesByBrewery() throws Exception {
        var session = login();
        profileWithCalcium(session, "iso", "50");

        var other = principal(UUID.randomUUID(), Set.of("water.read"));
        mockMvc.perform(get("/api/v1/water/profiles").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    private String sourceWithCalcium(MockHttpSession session, String code, String calcium) throws Exception {
        var body = mockMvc.perform(post("/api/v1/water/sources").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Fonte\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(body).get("id").asText();
        mockMvc.perform(post("/api/v1/water/sources/" + id + "/reports").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"collectedOn\":\"2026-01-10\",\"method\":\"LAB\",\"calcium\":" + calcium
                                + ",\"magnesium\":0,\"sodium\":0,\"sulfate\":0,\"chloride\":0,\"bicarbonate\":0}"))
                .andExpect(status().isCreated());
        return id;
    }

    private String profileWithCalcium(MockHttpSession session, String code, String calcium) throws Exception {
        var body = mockMvc.perform(post("/api/v1/water/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Alvo\",\"calcium\":" + calcium
                                + ",\"magnesium\":0,\"sodium\":0,\"sulfate\":0,\"chloride\":0,\"bicarbonate\":0}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
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

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
