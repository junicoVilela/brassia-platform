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
class ConsumptionIT {

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
    void recordsConsumptionUpsertsAndSummarizes() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);

        var c1 = completedCycle(session, code, equipmentId);
        recordConsumption(session, c1, 100, 5, 2).andExpect(status().isNoContent());
        // Upsert: reescreve o consumo do mesmo ciclo.
        recordConsumption(session, c1, 90, 4, 2).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + c1).session(session))
                .andExpect(jsonPath("$.consumption.waterLiters", is(90.0)));

        var c2 = completedCycle(session, code, equipmentId);
        recordConsumption(session, c2, 130, 6, 3).andExpect(status().isNoContent());

        // Comparação consultiva por POP agrega os dois ciclos.
        mockMvc.perform(get("/api/v1/sanitation/consumption/summary").session(session)
                        .param("procedureCode", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleCount", is(2)))
                .andExpect(jsonPath("$.minWaterLiters", is(90.0)))
                .andExpect(jsonPath("$.maxWaterLiters", is(130.0)));
    }

    @Test
    void consumptionRequiresEndedExecution() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var inProgress = startedCycle(session, code, equipmentId);
        recordConsumption(session, inProgress, 100, 5, 2).andExpect(status().isConflict());
    }

    @Test
    void deniesRecordWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + UUID.randomUUID() + "/consumption")
                        .with(authentication(principal(Set.of("sanitation.cycle.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"waterLiters\":10,\"energyKwh\":1,\"productKg\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summaryIsTenantIsolated() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var c1 = completedCycle(session, code, equipmentId);
        recordConsumption(session, c1, 100, 5, 2).andExpect(status().isNoContent());
        // Outra cervejaria não enxerga o consumo → resumo vazio.
        mockMvc.perform(get("/api/v1/sanitation/consumption/summary")
                        .with(authentication(principal(Set.of("sanitation.consumption.read"))))
                        .param("procedureCode", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleCount", is(0)));
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions recordConsumption(
            MockHttpSession session, String cycleId, int water, int energy, int product) throws Exception {
        return mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/consumption").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"waterLiters\":" + water + ",\"energyKwh\":" + energy + ",\"productKg\":" + product + "}"));
    }

    private String startedCycle(MockHttpSession session, String code, UUID equipmentId) throws Exception {
        var body = mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String completedCycle(MockHttpSession session, String code, UUID equipmentId) throws Exception {
        var id = startedCycle(session, code, equipmentId);
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
