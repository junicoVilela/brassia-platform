package br.com.brew.brassia.sanitation;

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
class VerifyReleaseIT {

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
    void verifiesAndReleasesWhenAllChecksPass() throws Exception {
        var session = login();
        var cycleId = completedCycle(session);

        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId).session(session))
                .andExpect(jsonPath("$.status", is("RELEASED")))
                .andExpect(jsonPath("$.verification.passed", is(true)))
                .andExpect(jsonPath("$.verification.atpOk", is(true)));
    }

    @Test
    void doesNotReleaseReprovedAndRejects() throws Exception {
        var session = login();
        var cycleId = completedCycle(session);

        // ATP acima do limite reprova.
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":150,\"atpThreshold\":100,\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/reject").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId).session(session))
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }

    @Test
    void releaseRequiresVerificationAndCompletion() throws Exception {
        var session = login();
        // Sem verificação, um ciclo concluído não libera.
        var completed = completedCycle(session);
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + completed + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict());
        // Verificar antes de concluir → 409.
        var inProgress = startedCycle(session);
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + inProgress + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,\"microOk\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesReleaseWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + UUID.randomUUID() + "/release")
                        .with(authentication(principal(Set.of("sanitation.cycle.execute")))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    /** Inicia um ciclo com um POP publicado de uma etapa e um equipamento; retorna o id. */
    private String startedCycle(MockHttpSession session) throws Exception {
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var body = mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    /** Ciclo iniciado + etapa registrada + concluído. */
    private String completedCycle(MockHttpSession session) throws Exception {
        var id = startedCycle(session);
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + id + "/steps").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + id + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        return id;
    }

    private String publishProcedure(MockHttpSession session) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var created = mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(created).get("id").asText();
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return code;
    }

    private UUID createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        var created = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Tanque\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72.5,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(created).get("id").asText());
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
