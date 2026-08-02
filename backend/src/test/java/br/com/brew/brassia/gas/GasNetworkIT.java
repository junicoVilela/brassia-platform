package br.com.brew.brassia.gas;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class GasNetworkIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CYLINDERS = "/api/v1/gas/cylinders";
    private static final String COMPONENTS = "/api/v1/gas/components";
    private static final String CONNECTIONS = "/api/v1/gas/connections";

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final String VALID_DUE = TODAY.plusYears(3).toString();
    private static final String EXPIRED_DUE = TODAY.minusDays(1).toString();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void connectionOnlyServesAfterAPassingLeakTest() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");

        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.status", is("PENDING_TEST")));
        // Sem teste aprovado, consumir é erro de registro.
        consume(session, connectionId, "1").andExpect(status().isConflict());

        leakTest(session, connectionId, true, null).andExpect(status().isOk());

        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).session(session))
                .andExpect(jsonPath("$.connection.status", is("SERVING")))
                .andExpect(jsonPath("$.connection.leakTest.passed", is(true)));
        consume(session, connectionId, "1").andExpect(status().isOk());
    }

    @Test
    void expiredCylinderIsNeverAllocated() throws Exception {
        var session = login();
        var scene = scene(session);
        var expired = cylinder(session, "10", EXPIRED_DUE);

        mockMvc.perform(get(CYLINDERS + "/" + expired).session(session))
                .andExpect(jsonPath("$.expired", is(true)))
                .andExpect(jsonPath("$.allocatable", is(false)));

        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(expired, scene.regulatorId, scene.manifoldId, scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("gas_connection_blocked")))
                .andExpect(jsonPath("$.blockers[*].code", contains("cylinder_expired")));
    }

    @Test
    void blockedCylinderIsNeverAllocatedAndUnblockingDoesNotRequalify() throws Exception {
        var session = login();
        var scene = scene(session);
        var expired = cylinder(session, "10", EXPIRED_DUE);

        block(session, expired, true, "requalificação vencida").andExpect(status().isOk());
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(expired, scene.regulatorId, scene.manifoldId, scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code",
                        containsInAnyOrder("cylinder_blocked", "cylinder_expired")));

        // Desbloquear não apaga o vencimento.
        block(session, expired, false, null).andExpect(status().isOk());
        mockMvc.perform(get(CYLINDERS + "/" + expired).session(session))
                .andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.allocatable", is(false)));

        // Requalificar libera.
        mockMvc.perform(post(CYLINDERS + "/" + expired + "/requalification").session(session).with(csrf())
                        .contentType("application/json").content("{\"dueOn\":\"" + VALID_DUE + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(CYLINDERS + "/" + expired).session(session))
                .andExpect(jsonPath("$.allocatable", is(true)));
    }

    @Test
    void blockingRequiresReason() throws Exception {
        var session = login();
        var cylinderId = cylinder(session, "10", VALID_DUE);

        block(session, cylinderId, true, null).andExpect(status().isBadRequest());
        mockMvc.perform(get(CYLINDERS + "/" + cylinderId).session(session))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    void refusesWorkingPressureAboveTheWeakestComponent() throws Exception {
        var session = login();
        var scene = scene(session);

        // Regulador aguenta 10 bar, manifold só 6: pedir 8 estoura a rede.
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                scene.pointId, "8")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", contains("working_pressure_above_network")));

        // O cilindro não foi ocupado pela tentativa recusada.
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    void overPressureReadingIsKeptAndBlocksTheLine() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");
        leakTest(session, connectionId, true, null).andExpect(status().isOk());

        pressure(session, connectionId, "5.5").andExpect(status().isOk())
                .andExpect(jsonPath("$.overPressure", is(false)))
                .andExpect(jsonPath("$.status", is("SERVING")));

        pressure(session, connectionId, "7").andExpect(status().isOk())
                .andExpect(jsonPath("$.overPressure", is(true)))
                .andExpect(jsonPath("$.status", is("BLOCKED")));

        // A leitura que denunciou a sobrepressão é preservada; a linha para de servir.
        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).session(session))
                .andExpect(jsonPath("$.connection.status", is("BLOCKED")))
                .andExpect(jsonPath("$.pressureReadings.length()", is(2)))
                .andExpect(jsonPath("$.pressureReadings[?(@.overPressure==true)].bar", is(java.util.List.of(7.0))));
        consume(session, connectionId, "1").andExpect(status().isConflict());

        // Só um novo teste aprovado devolve a linha ao serviço.
        leakTest(session, connectionId, true, null).andExpect(status().isOk());
        consume(session, connectionId, "1").andExpect(status().isOk());
    }

    @Test
    void failedLeakTestBlocksTheLineAndRequiresANote() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");

        leakTest(session, connectionId, false, null).andExpect(status().isBadRequest());

        leakTest(session, connectionId, false, "bolhas na conexão do regulador").andExpect(status().isOk());
        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).session(session))
                .andExpect(jsonPath("$.connection.status", is("BLOCKED")))
                .andExpect(jsonPath("$.connection.leakTest.passed", is(false)));
        consume(session, connectionId, "1").andExpect(status().isConflict());
    }

    @Test
    void consumptionReducesContentAndCannotExceedIt() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");
        leakTest(session, connectionId, true, null).andExpect(status().isOk());

        consume(session, connectionId, "4").andExpect(status().isOk());
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.contentKg", is(6.0)));

        consume(session, connectionId, "7").andExpect(status().isBadRequest());
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.contentKg", is(6.0)));

        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).session(session))
                .andExpect(jsonPath("$.consumedKg", is(4.0)))
                .andExpect(jsonPath("$.consumption.length()", is(1)));
    }

    @Test
    void emptyCylinderIsNotAllocatedUntilRefilled() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");
        leakTest(session, connectionId, true, null).andExpect(status().isOk());
        consume(session, connectionId, "10").andExpect(status().isOk());
        disconnect(session, connectionId, "cilindro vazio").andExpect(status().isOk());

        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.status", is("EMPTY")))
                .andExpect(jsonPath("$.allocatable", is(false)));
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("cylinder_empty")));

        mockMvc.perform(post(CYLINDERS + "/" + scene.cylinderId + "/refill").session(session).with(csrf())
                        .contentType("application/json").content("{\"contentKg\":9.8}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.status", is("AVAILABLE")))
                .andExpect(jsonPath("$.allocatable", is(true)));
    }

    @Test
    void cylinderAndPointOfUseServeOneConnectionAtATime() throws Exception {
        var session = login();
        var scene = scene(session);
        connect(session, scene, "3");

        // Mesmo cilindro em outro ponto: em uso.
        var otherPoint = equipment(session);
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                otherPoint, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("cylinder_in_use")));

        // Outro cilindro no mesmo ponto: ponto ocupado.
        var another = cylinder(session, "10", VALID_DUE);
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(another, scene.regulatorId, scene.manifoldId, scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("point_of_use_occupied")));
    }

    @Test
    void disconnectingFreesTheCylinderAndThePoint() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");

        disconnect(session, connectionId, "troca de cilindro").andExpect(status().isOk());
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).session(session))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
        // Desconectar é terminal.
        disconnect(session, connectionId, "de novo").andExpect(status().isConflict());

        // Ponto e cilindro livres para uma nova linha.
        connect(session, scene, "3");
    }

    @Test
    void refusesInactiveComponentAndComponentWithTheWrongRole() throws Exception {
        var session = login();
        var scene = scene(session);

        mockMvc.perform(post(COMPONENTS + "/" + scene.regulatorId + "/active").session(session).with(csrf())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("regulator_inactive")));

        // Manifold no lugar do regulador é impedimento, não confusão silenciosa.
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.manifoldId, null, scene.pointId, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("regulator_unknown")));
    }

    @Test
    void regulatorCannotBeSetAboveItsOwnLimitAndManifoldHasNoSetPressure() throws Exception {
        var session = login();
        var sfx = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post(COMPONENTS).session(session).with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"REGULATOR\",\"code\":\"R-" + sfx + "\",\"name\":\"Reg\","
                                + "\"maxPressureBar\":10,\"setPressureBar\":12}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(COMPONENTS).session(session).with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"MANIFOLD\",\"code\":\"M-" + sfx + "\",\"name\":\"Man\","
                                + "\"maxPressureBar\":6,\"setPressureBar\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateCodeAndUnknownPointOfUse() throws Exception {
        var session = login();
        var code = "CIL-" + UUID.randomUUID().toString().substring(0, 8);

        registerCylinder(session, code, "10", VALID_DUE).andExpect(status().isCreated());
        registerCylinder(session, code, "10", VALID_DUE).andExpect(status().isConflict());

        var scene = scene(session);
        mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                UUID.randomUUID().toString(), "3")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var scene = scene(session);

        mockMvc.perform(post(CONNECTIONS)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("gas.read")))).with(csrf())
                        .contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                scene.pointId, "3")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CYLINDERS).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var scene = scene(session);
        var connectionId = connect(session, scene, "3");

        var other = principal(UUID.randomUUID(), Set.of("gas.read", "gas.manage"));
        mockMvc.perform(get(CYLINDERS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(CYLINDERS + "/" + scene.cylinderId).with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CONNECTIONS + "/" + connectionId).with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/disconnect").with(authentication(other))
                        .with(csrf()).contentType("application/json").content("{\"reason\":\"invasão\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    /** Cilindro cheio e válido, regulador de 10 bar, manifold de 6 bar e um ponto de uso. */
    private record Scene(String cylinderId, String regulatorId, String manifoldId, String pointId) {}

    private Scene scene(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var regulator = component(session, "REGULATOR", "R-" + sfx, "10", "3");
        var manifold = component(session, "MANIFOLD", "M-" + sfx, "6", null);
        return new Scene(cylinder(session, "10", VALID_DUE), regulator, manifold, equipment(session));
    }

    // --- helpers ---

    private String connect(MockHttpSession session, Scene scene, String workingBar) throws Exception {
        var body = mockMvc.perform(post(CONNECTIONS).session(session).with(csrf()).contentType("application/json")
                        .content(connectBody(scene.cylinderId, scene.regulatorId, scene.manifoldId,
                                scene.pointId, workingBar)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String connectBody(String cylinderId, String regulatorId, String manifoldId, String pointId,
            String workingBar) {
        return """
                {"cylinderId":"%s","regulatorId":"%s",%s"pointOfUseEquipmentId":"%s","workingPressureBar":%s}
                """.formatted(cylinderId, regulatorId,
                manifoldId == null ? "" : "\"manifoldId\":\"" + manifoldId + "\",", pointId, workingBar);
    }

    private ResultActions leakTest(MockHttpSession session, String connectionId, boolean passed, String note)
            throws Exception {
        var content = "{\"passed\":" + passed + ",\"method\":\"espuma + queda de pressão\","
                + "\"pressureDropBar\":" + (passed ? "0" : "0.4")
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
        return mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/leak-test").session(session).with(csrf())
                .contentType("application/json").content(content));
    }

    private ResultActions pressure(MockHttpSession session, String connectionId, String bar) throws Exception {
        return mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/pressure").session(session).with(csrf())
                .contentType("application/json").content("{\"bar\":" + bar + ",\"tempC\":18}"));
    }

    private ResultActions consume(MockHttpSession session, String connectionId, String kg) throws Exception {
        return mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/consumption").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"kg\":" + kg + ",\"reason\":\"carbonatação\"}"));
    }

    private ResultActions disconnect(MockHttpSession session, String connectionId, String reason)
            throws Exception {
        return mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/disconnect").session(session)
                .with(csrf()).contentType("application/json").content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions block(MockHttpSession session, String cylinderId, boolean blocked, String reason)
            throws Exception {
        var content = "{\"blocked\":" + blocked + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
        return mockMvc.perform(post(CYLINDERS + "/" + cylinderId + "/block").session(session).with(csrf())
                .contentType("application/json").content(content));
    }

    private String cylinder(MockHttpSession session, String contentKg, String dueOn) throws Exception {
        var code = "CIL-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(registerCylinder(session, code, contentKg, dueOn)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions registerCylinder(MockHttpSession session, String code, String contentKg, String dueOn)
            throws Exception {
        return mockMvc.perform(post(CYLINDERS).session(session).with(csrf()).contentType("application/json")
                .content("{\"code\":\"" + code + "\",\"gasType\":\"CO2\",\"capacityKg\":10,\"tareKg\":12.5,"
                        + "\"contentKg\":" + contentKg + ",\"requalificationDueOn\":\"" + dueOn + "\","
                        + "\"location\":\"Casa de gases\"}"));
    }

    private String component(MockHttpSession session, String kind, String code, String maxBar, String setBar)
            throws Exception {
        var content = "{\"kind\":\"" + kind + "\",\"code\":\"" + code + "\",\"name\":\"" + code + "\","
                + "\"maxPressureBar\":" + maxBar
                + (setBar == null ? "" : ",\"setPressureBar\":" + setBar) + "}";
        return idOf(mockMvc.perform(post(COMPONENTS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String equipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Tanque\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
