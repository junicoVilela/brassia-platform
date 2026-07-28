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
import com.fasterxml.jackson.databind.JsonNode;
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
class CleaningCycleIT {

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
    void executesFullCycleWithOverrideOutOfOrderAndCompletion() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var cycleId = startCycle(session, code, equipmentId);

        // Etapa 1 dentro da faixa.
        recordStep(session, cycleId,
                "{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,\"measuredTimeMinutes\":20}")
                .andExpect(status().isNoContent());

        // Parâmetro fora da ficha (temperatura acima do máximo) sem override → 400.
        recordStep(session, cycleId,
                "{\"sequence\":2,\"measuredConcentrationPct\":2.0,\"measuredTempC\":95,\"measuredTimeMinutes\":20}")
                .andExpect(status().isBadRequest());

        // Override sem alçada → 403.
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sanitation.cycle.execute"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"sequence\":2,\"measuredTempC\":95,\"override\":true,\"overrideReason\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Override com alçada + justificativa → 204.
        recordStep(session, cycleId,
                "{\"sequence\":2,\"measuredConcentrationPct\":2.0,\"measuredTempC\":95,\"measuredTimeMinutes\":20,"
                        + "\"override\":true,\"overrideReason\":\"linha validada fora da faixa\"}")
                .andExpect(status().isNoContent());

        // Conclui e confere o estado congelado/execução.
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.steps[1].overridden", is(true)));
    }

    @Test
    void outOfOrderRequiresReasonAndRepeatIsBlocked() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var cycleId = startCycle(session, code, equipmentId);

        // Etapa 2 antes da 1 sem motivo → 400.
        recordStep(session, cycleId,
                "{\"sequence\":2,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,\"measuredTimeMinutes\":20}")
                .andExpect(status().isBadRequest());
        // Com motivo → 204.
        recordStep(session, cycleId,
                "{\"sequence\":2,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,\"measuredTimeMinutes\":20,"
                        + "\"outOfOrderReason\":\"operador priorizou\"}")
                .andExpect(status().isNoContent());
        // Repetir etapa já registrada → 409.
        recordStep(session, cycleId,
                "{\"sequence\":2,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,\"measuredTimeMinutes\":20,"
                        + "\"outOfOrderReason\":\"de novo\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void interruptPreservesAndResumes() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var cycleId = startCycle(session, code, equipmentId);

        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/interrupt").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"falta de químico\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId).session(session))
                .andExpect(jsonPath("$.status", is("INTERRUPTED")))
                .andExpect(jsonPath("$.interruptReason", is("falta de químico")));
        // Registrar em ciclo interrompido → 409.
        recordStep(session, cycleId,
                "{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,\"measuredTimeMinutes\":20}")
                .andExpect(status().isConflict());
        // Retomar → volta a andamento.
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/resume").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId).session(session))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    void rejectsUnpublishedProcedureAndUnknownEquipment() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        // Código sem POP publicado → 400.
        mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"NAO-EXISTE\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isBadRequest());
        // Equipamento inexistente → 400.
        var code = publishProcedure(session);
        mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session);
        var code = publishProcedure(session);
        var cycleId = startCycle(session, code, equipmentId);
        // Outra cervejaria não enxerga o ciclo → 400.
        mockMvc.perform(get("/api/v1/sanitation/cycles/" + cycleId)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sanitation.cycle.read")))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String startCycle(MockHttpSession session, String code, UUID equipmentId) throws Exception {
        var body = mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions recordStep(
            MockHttpSession session, String cycleId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps").session(session).with(csrf())
                .contentType("application/json").content(json));
    }

    /** Cria um POP com duas etapas (1–3%, 50–70°C, 15 min) e o publica; retorna o código. */
    private String publishProcedure(MockHttpSession session) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step1 = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var step2 = "{\"sequence\":2,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var created = mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP de tanque\",\"steps\":["
                                + step1 + "," + step2 + "]}"))
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
        JsonNode node = JSON.readTree(created);
        return UUID.fromString(node.get("id").asText());
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
