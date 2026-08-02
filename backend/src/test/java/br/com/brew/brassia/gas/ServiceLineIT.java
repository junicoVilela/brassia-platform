package br.com.brew.brassia.gas;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ServiceLineIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LINES = "/api/v1/gas/service-lines";
    private static final String TUBING = "/api/v1/gas/tubing";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void balanceExplainsPressureLengthAndTheInputsThatDefineThem() throws Exception {
        var session = login();
        var scene = scene(session);

        // 2,5 vol a 4 °C pedem 0,81 bar; torneira 0,305 m acima; tubo de 0,679 bar/m → ~1,05 m.
        balance(session, scene, "2.5", "4", "0.305", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPressureBar", closeTo(0.81, 0.05)))
                .andExpect(jsonPath("$.recommendedLengthMeters", closeTo(1.05, 0.1)))
                .andExpect(jsonPath("$.hydrostaticBar", closeTo(0.030, 0.002)))
                .andExpect(jsonPath("$.feasible", is(true)))
                // Método, diâmetro, material, desnível, vazão e temperatura vêm explícitos.
                .andExpect(jsonPath("$.material", is("vinil")))
                .andExpect(jsonPath("$.internalDiameterMm", is(4.8)))
                .andExpect(jsonPath("$.targetFlowLpm", is(1)))
                .andExpect(jsonPath("$.servingTempC", is(4)))
                .andExpect(jsonPath("$.calculationMethod", notNullValue()))
                .andExpect(jsonPath("$.calculatorVersion", notNullValue()));
    }

    @Test
    void everyRecommendationSaysNoValveIsAdjustedAutomatically() throws Exception {
        var session = login();
        var scene = scene(session);

        balance(session, scene, "2.5", "4", "0.305", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[*].code", hasItem("manual_adjustment_only")))
                .andExpect(jsonPath("$.warnings[?(@.code=='manual_adjustment_only')].safety",
                        is(java.util.List.of(true))));
    }

    @Test
    void higherFlowShortensTheLineAndColderServiceLowersPressure() throws Exception {
        var session = login();
        var scene = scene(session);

        var normal = value(balance(session, scene, "2.5", "4", "0", "1"), "recommendedLengthMeters");
        var faster = value(balance(session, scene, "2.5", "4", "0", "2"), "recommendedLengthMeters");
        // Laminar: dobrar a vazão dobra a resistência efetiva, então a linha cai à metade.
        org.assertj.core.api.Assertions.assertThat(faster).isCloseTo(normal / 2,
                org.assertj.core.data.Offset.offset(0.05));

        var cold = value(balance(session, scene, "2.5", "2", "0", "1"), "appliedPressureBar");
        var warm = value(balance(session, scene, "2.5", "12", "0", "1"), "appliedPressureBar");
        // A mesma carbonatação exige mais pressão numa cerveja mais quente.
        org.assertj.core.api.Assertions.assertThat(warm).isGreaterThan(cold);
    }

    @Test
    void impossibleSetupIsFlaggedAsUnfeasibleWithSafetyWarning() throws Exception {
        var session = login();
        var scene = scene(session);

        // 10 m de subida consomem mais pressão do que a carbonatação sustenta.
        balance(session, scene, "2.5", "4", "10", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible", is(false)))
                .andExpect(jsonPath("$.recommendedLengthMeters", is(0)))
                .andExpect(jsonPath("$.warnings[*].code", hasItem("no_balance_possible")));
    }

    @Test
    void pressureAboveTheGasNetworkLimitIsWarned() throws Exception {
        var session = login();
        var scene = scene(session);
        // Rede montada no mesmo ponto com teto de 0,5 bar; servir 2,5 vol a 12 °C pede ~1,36 bar.
        connectGasAt(session, scene.pointId, "0.5");

        balance(session, scene, "2.5", "12", "0", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[*].code", hasItem("above_network_limit")))
                .andExpect(jsonPath("$.warnings[?(@.code=='above_network_limit')].safety",
                        is(java.util.List.of(true))));
    }

    @Test
    void applyingCreatesARevisionAndKeepsThePreviousOne() throws Exception {
        var session = login();
        var scene = scene(session);

        apply(session, scene, "2.5", "4", "0.305", "1", "1.20", "montagem inicial")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision", is(1)))
                .andExpect(jsonPath("$.recommendedLengthMeters", closeTo(1.05, 0.1)))
                // O desvio entre o montado e o recomendado fica explícito.
                .andExpect(jsonPath("$.lengthDeviationMeters", closeTo(0.15, 0.1)));

        apply(session, scene, "2.4", "6", "0.305", "1", "1.00", "encurtei a linha")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision", is(2)));

        mockMvc.perform(get(LINES + "/" + scene.lineId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.currentRevision", is(2)))
                .andExpect(jsonPath("$.line.everApplied", is(true)))
                // A revisão anterior é preservada: ela explica a cerveja servida ontem.
                .andExpect(jsonPath("$.revisions.length()", is(2)))
                .andExpect(jsonPath("$.revisions[0].revision", is(2)))
                .andExpect(jsonPath("$.revisions[0].appliedLengthMeters", is(1.0)))
                .andExpect(jsonPath("$.revisions[1].revision", is(1)))
                .andExpect(jsonPath("$.revisions[1].appliedLengthMeters", is(1.2)))
                .andExpect(jsonPath("$.revisions[1].note", is("montagem inicial")))
                .andExpect(jsonPath("$.revisions[1].calculatorVersion", notNullValue()));
    }

    @Test
    void newLineHasNoAppliedAssembly() throws Exception {
        var session = login();
        var scene = scene(session);

        mockMvc.perform(get(LINES + "/" + scene.lineId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line.currentRevision", is(0)))
                .andExpect(jsonPath("$.line.everApplied", is(false)))
                .andExpect(jsonPath("$.revisions").isEmpty());
    }

    @Test
    void tubingIsIdentifiedByMaterialAndDiameterSoRegisteringAgainUpdatesTheNumbers() throws Exception {
        var session = login();
        var first = tubing(session, "vinil", "4.8", "0.679", "1");
        var again = tubing(session, "vinil", "4.8", "0.700", "1.5");

        org.assertj.core.api.Assertions.assertThat(again).isEqualTo(first);
        mockMvc.perform(get(TUBING).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + first + "')].resistanceBarPerMeter",
                        is(java.util.List.of(0.7))))
                .andExpect(jsonPath("$[?(@.id=='" + first + "')].referenceFlowLpm",
                        is(java.util.List.of(1.5))));
    }

    @Test
    void rejectsDuplicateLineCodeAndUnknownPointOfUse() throws Exception {
        var session = login();
        var code = "LN-" + UUID.randomUUID().toString().substring(0, 8);
        var pointId = createEquipment(session);

        registerLine(session, code, pointId).andExpect(status().isCreated());
        registerLine(session, code, pointId).andExpect(status().isConflict());
        registerLine(session, "LN-X" + code, UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownTubingAndNonPositiveNumbers() throws Exception {
        var session = login();
        var scene = scene(session);

        mockMvc.perform(get(LINES + "/" + scene.lineId + "/balance").session(session)
                        .param("targetCo2Volumes", "2.5").param("servingTempC", "4")
                        .param("elevationMeters", "0").param("residualPressureBar", "0.069")
                        .param("targetFlowLpm", "1").param("resistanceId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(TUBING).session(session).with(csrf()).contentType("application/json")
                        .content("{\"material\":\"vinil\",\"internalDiameterMm\":0,"
                                + "\"resistanceBarPerMeter\":0.679,\"referenceFlowLpm\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var scene = scene(session);

        mockMvc.perform(post(LINES + "/" + scene.lineId + "/revisions")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("gas.read")))).with(csrf())
                        .contentType("application/json")
                        .content(applyBody("2.5", "4", "0.305", "1", scene.tubingId, "1.20", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(LINES).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var scene = scene(session);
        apply(session, scene, "2.5", "4", "0.305", "1", "1.20", null).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("gas.read", "gas.manage"));
        mockMvc.perform(get(LINES).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(LINES + "/" + scene.lineId).with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(LINES + "/" + scene.lineId + "/revisions").with(authentication(other)).with(csrf())
                        .contentType("application/json")
                        .content(applyBody("2.5", "4", "0.305", "1", scene.tubingId, "1.20", null)))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    private record Scene(String lineId, String tubingId, String pointId) {}

    /** Linha de serviço num ponto de uso, com um tubo de vinil 3/16" no catálogo. */
    private Scene scene(MockHttpSession session) throws Exception {
        var pointId = createEquipment(session);
        var code = "LN-" + UUID.randomUUID().toString().substring(0, 8);
        var body = registerLine(session, code, pointId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Scene(JSON.readTree(body).get("id").asText(),
                tubing(session, "vinil", "4.8", "0.679", "1"), pointId);
    }

    // --- helpers ---

    private ResultActions registerLine(MockHttpSession session, String code, String pointId) throws Exception {
        return mockMvc.perform(post(LINES).session(session).with(csrf()).contentType("application/json")
                .content("{\"code\":\"" + code + "\",\"name\":\"Torneira " + code + "\","
                        + "\"pointOfUseEquipmentId\":\"" + pointId + "\"}"));
    }

    private String tubing(MockHttpSession session, String material, String diameter, String resistance,
            String referenceFlow) throws Exception {
        var body = mockMvc.perform(post(TUBING).session(session).with(csrf()).contentType("application/json")
                        .content("{\"material\":\"" + material + "\",\"internalDiameterMm\":" + diameter
                                + ",\"resistanceBarPerMeter\":" + resistance
                                + ",\"referenceFlowLpm\":" + referenceFlow + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private ResultActions balance(MockHttpSession session, Scene scene, String volumes, String tempC,
            String elevation, String flow) throws Exception {
        return mockMvc.perform(get(LINES + "/" + scene.lineId + "/balance").session(session)
                .param("targetCo2Volumes", volumes).param("servingTempC", tempC)
                .param("elevationMeters", elevation).param("residualPressureBar", "0.069")
                .param("targetFlowLpm", flow).param("resistanceId", scene.tubingId));
    }

    private ResultActions apply(MockHttpSession session, Scene scene, String volumes, String tempC,
            String elevation, String flow, String appliedLength, String note) throws Exception {
        return mockMvc.perform(post(LINES + "/" + scene.lineId + "/revisions").session(session).with(csrf())
                .contentType("application/json")
                .content(applyBody(volumes, tempC, elevation, flow, scene.tubingId, appliedLength, note)));
    }

    private static String applyBody(String volumes, String tempC, String elevation, String flow,
            String tubingId, String appliedLength, String note) {
        return """
                {"targetCo2Volumes":%s,"servingTempC":%s,"elevationMeters":%s,"residualPressureBar":0.069,
                 "targetFlowLpm":%s,"resistanceId":"%s","appliedLengthMeters":%s%s}
                """.formatted(volumes, tempC, elevation, flow, tubingId, appliedLength,
                note == null ? "" : ",\"note\":\"" + note + "\"");
    }

    private double value(ResultActions actions, String field) throws Exception {
        var body = actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(field).asDouble();
    }

    /** Monta uma linha de gás no ponto, para o teto da rede virar limite do balanceamento. */
    private void connectGasAt(MockHttpSession session, String pointId, String maxBar) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var regulator = idOf(mockMvc.perform(post("/api/v1/gas/components").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"REGULATOR\",\"code\":\"R-" + sfx + "\",\"name\":\"Reg\","
                                + "\"maxPressureBar\":" + maxBar + ",\"setPressureBar\":0.3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var cylinder = idOf(mockMvc.perform(post("/api/v1/gas/cylinders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"CIL-" + sfx + "\",\"gasType\":\"CO2\",\"capacityKg\":10,"
                                + "\"tareKg\":12.5,\"contentKg\":10,\"requalificationDueOn\":\"2030-01-01\","
                                + "\"location\":\"Casa de gases\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/gas/connections").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"cylinderId\":\"" + cylinder + "\",\"regulatorId\":\"" + regulator
                                + "\",\"pointOfUseEquipmentId\":\"" + pointId
                                + "\",\"workingPressureBar\":0.3}"))
                .andExpect(status().isCreated());
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Torneira\",\"capacityLiters\":50,"
                                + "\"deadSpaceLiters\":1,\"mashEfficiencyPercent\":72,"
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
