package br.com.brew.brassia.fermentation;

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
class FgStabilityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String READINGS = "/api/v1/fermentation/readings";
    private static final String STAGE = "{\"sequence\":1,\"name\":\"Primária\",\"targetTempC\":18.0,"
            + "\"condition\":\"MANUAL\",\"requiresConfirmation\":true}";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reportsStableSeriesWithTheReadingsItUsed() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        planSchedule(session, batchId, publishedProfile(session, null));

        density(session, batchId, "1.0125", "2026-07-28T08:00:00Z");
        density(session, batchId, "1.0120", "2026-07-29T08:00:00Z");
        density(session, batchId, "1.0118", "2026-07-30T08:00:00Z");

        mockMvc.perform(get(url(batchId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stable", is(true)))
                .andExpect(jsonPath("$.verdict", is("STABLE")))
                .andExpect(jsonPath("$.spanHours", is(48)))
                // O parecer explica: critério aplicado + leituras que o sustentam.
                .andExpect(jsonPath("$.policy.windowHours", is(48)))
                .andExpect(jsonPath("$.readings.length()", is(3)))
                .andExpect(jsonPath("$.readings[0].kind", is("DENSITY")));
    }

    @Test
    void rejectsFalseStabilityWhenTheWindowIsNotCovered() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        planSchedule(session, batchId, publishedProfile(session, null));

        // Três leituras quase idênticas na mesma tarde: o clássico FG falso estável.
        density(session, batchId, "1.0120", "2026-07-28T08:00:00Z");
        density(session, batchId, "1.0119", "2026-07-28T10:00:00Z");
        density(session, batchId, "1.0120", "2026-07-28T12:00:00Z");

        mockMvc.perform(get(url(batchId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stable", is(false)))
                .andExpect(jsonPath("$.verdict", is("WINDOW_NOT_COVERED")))
                .andExpect(jsonPath("$.readings.length()", is(3)));
    }

    @Test
    void appliesTheCriterionFrozenInTheProfileVersion() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        // Perfil exigente: janela de 96h — a mesma série de 48h deixa de bastar.
        var strict = publishedProfile(session, "{\"windowHours\":96,\"minReadings\":3,\"toleranceSg\":0.0020}");
        planSchedule(session, batchId, strict);

        density(session, batchId, "1.0125", "2026-07-28T08:00:00Z");
        density(session, batchId, "1.0120", "2026-07-29T08:00:00Z");
        density(session, batchId, "1.0118", "2026-07-30T08:00:00Z");

        mockMvc.perform(get(url(batchId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stable", is(false)))
                .andExpect(jsonPath("$.verdict", is("WINDOW_NOT_COVERED")))
                .andExpect(jsonPath("$.policy.windowHours", is(96)));
    }

    @Test
    void ignoresFlaggedReadings() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        planSchedule(session, batchId, publishedProfile(session, null));

        density(session, batchId, "1.0125", "2026-07-28T08:00:00Z");
        // Sensor ruidoso: gravada e sinalizada pela FER-002, não sustenta parecer.
        mockMvc.perform(post(READINGS).session(session).with(csrf()).contentType("application/json")
                        .content(body(batchId, "1.5000", "2026-07-29T00:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valid", is(false)));
        density(session, batchId, "1.0120", "2026-07-29T08:00:00Z");
        density(session, batchId, "1.0118", "2026-07-30T08:00:00Z");

        mockMvc.perform(get(url(batchId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stable", is(true)))
                .andExpect(jsonPath("$.readings.length()", is(3)));
    }

    @Test
    void refusesDraftProfileAsCriterion() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var draft = createProfile(session, null);

        // O vínculo nasce na agenda: é lá que o rascunho é barrado.
        mockMvc.perform(post("/api/v1/fermentation/batches/" + batchId + "/schedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"profileId\":\"" + draft + "\",\"start\":\"2026-07-28T08:00:00Z\","
                                + "\"responsibleUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownBatchOrBatchWithoutSchedule() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(get(url(UUID.randomUUID().toString())).session(session))
                .andExpect(status().isBadRequest());
        // Sem agenda não há critério a aplicar.
        mockMvc.perform(get(url(batchId)).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        planSchedule(session, batchId, publishedProfile(session, null));

        mockMvc.perform(get(url(batchId))
                        .with(authentication(principal(UUID.randomUUID(), Set.of("fermentation.profile.read")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        planSchedule(session, batchId, publishedProfile(session, null));

        mockMvc.perform(get(url(batchId))
                        .with(authentication(principal(UUID.randomUUID(), Set.of("fermentation.reading.read")))))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private static String url(String batchId) {
        return "/api/v1/fermentation/batches/" + batchId + "/fg-stability";
    }

    /**
     * Desde a FER-004 o perfil vem da agenda do lote, não por parâmetro: planejar a agenda é
     * o que dá ao lote o critério de estabilidade.
     */
    private void planSchedule(MockHttpSession session, String batchId, String profileId) throws Exception {
        mockMvc.perform(post("/api/v1/fermentation/batches/" + batchId + "/schedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"profileId\":\"" + profileId + "\",\"start\":\"2026-07-28T08:00:00Z\","
                                + "\"responsibleUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
    }

    private static String body(String batchId, String value, String at) {
        return "{\"batchId\":\"" + batchId + "\",\"kind\":\"DENSITY\",\"source\":\"SENSOR\",\"value\":" + value
                + ",\"unit\":\"SG\",\"measuredAt\":\"" + at + "\"}";
    }

    private void density(MockHttpSession session, String batchId, String value, String at) throws Exception {
        mockMvc.perform(post(READINGS).session(session).with(csrf()).contentType("application/json")
                        .content(body(batchId, value, at)))
                .andExpect(status().isCreated());
    }

    private String createProfile(MockHttpSession session, String stabilityJson) throws Exception {
        var code = "FG-" + UUID.randomUUID().toString().substring(0, 6);
        var content = "{\"code\":\"" + code + "\",\"name\":\"FG\",\"stages\":[" + STAGE + "]"
                + (stabilityJson == null ? "" : ",\"stability\":" + stabilityJson) + "}";
        var body = mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String publishedProfile(MockHttpSession session, String stabilityJson) throws Exception {
        var id = createProfile(session, stabilityJson);
        mockMvc.perform(post("/api/v1/fermentation/profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody)) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"FG %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                                + attributes + "}"))
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
